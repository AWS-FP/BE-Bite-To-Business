package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.exception.ErrorResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Expands a cart into physical stock, including overlapping combo and standalone lines. */
@Service
public class CheckoutStockService {
    private final NamedParameterJdbcTemplate jdbc;

    public CheckoutStockService(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public record Line(UUID productId, BigDecimal quantity) {}
    record Component(UUID productId, BigDecimal factor, BigDecimal available, boolean combo) {}

    public Map<UUID, BigDecimal> available(List<Line> lines) {
        if (lines.isEmpty()) return Map.of();
        Map<UUID, List<Component>> components = new HashMap<>();
        jdbc.query("""
                select c.sellable_id,c.product_id,c.factor,p.unit_code,
                       greatest(coalesce(i.available_quantity,0),0) available
                from checkout_stock.components c
                join public.products p on p.id=c.sellable_id
                left join public.inventory i on i.product_id=c.product_id
                where c.sellable_id in (:ids)
                """, Map.of("ids", lines.stream().map(Line::productId).distinct().toList()), rs -> {
            UUID id = rs.getObject("sellable_id", UUID.class);
            components.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new Component(
                    rs.getObject("product_id", UUID.class), rs.getBigDecimal("factor"),
                    rs.getBigDecimal("available"), "COMBO".equals(rs.getString("unit_code"))));
        });
        return calculateAvailability(lines, components);
    }

    static Map<UUID, BigDecimal> calculateAvailability(List<Line> lines, Map<UUID, List<Component>> components) {
        Map<UUID, BigDecimal> totals = new HashMap<>();
        for (Line line : lines) {
            for (Component component : components.getOrDefault(line.productId(), List.of())) {
                totals.merge(component.productId(), line.quantity().multiply(component.factor()), BigDecimal::add);
            }
        }
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (Line line : lines) {
            BigDecimal capacity = null;
            for (Component component : components.getOrDefault(line.productId(), List.of())) {
                BigDecimal others = totals.get(component.productId()).subtract(line.quantity().multiply(component.factor()));
                BigDecimal remaining = component.available().subtract(others).max(BigDecimal.ZERO);
                BigDecimal possible = component.combo() ? remaining.divide(component.factor(), 0, RoundingMode.DOWN) : remaining;
                capacity = capacity == null ? possible : capacity.min(possible);
            }
            result.put(line.productId(), capacity == null ? BigDecimal.ZERO : capacity);
        }
        return result;
    }

    public void requireAvailable(List<Line> lines) {
        Map<UUID, BigDecimal> available = available(lines);
        List<ErrorResponse.ValidationError> shortages = new ArrayList<>();
        for (Line line : lines) {
            BigDecimal stock = available.getOrDefault(line.productId(), BigDecimal.ZERO);
            if (stock.compareTo(line.quantity()) < 0) {
                shortages.add(StockShortage.of(line.productId(), stock, line.quantity()));
            }
        }
        if (!shortages.isEmpty()) throw new BusinessException(ErrorCode.QUOTE_STOCK_UNAVAILABLE, shortages);
    }

    public static List<Line> quoteLines(List<QuoteItem> items) {
        return items.stream().map(item -> new Line(item.getProductId(), item.getQuantity())).toList();
    }

    /** Called in the quote transaction after the quote line has been flushed. */
    public void snapshot(QuoteItem item) {
        if (!"COMBO".equals(item.getUnitCode())) return;
        var params = Map.of("item", item.getId(), "product", item.getProductId());
        jdbc.update("delete from public.quote_combo_items where quote_item_id=:item", params);
        int count = jdbc.update("""
                insert into public.quote_combo_items(quote_item_id,product_id,quantity_per_combo,unit_code)
                select :item,product_id,factor,unit_code from checkout_stock.components where sellable_id=:product
                """, params);
        if (count == 0) throw new BusinessException(ErrorCode.QUOTE_STOCK_UNAVAILABLE);
    }

    /** An approved quote cannot silently change its ingredient quantities or units. */
    public boolean snapshotMatches(QuoteItem item) {
        if (!"COMBO".equals(item.getUnitCode())) return true;
        Boolean matches = jdbc.queryForObject("""
                with current_items as (
                    select product_id,factor,unit_code from checkout_stock.components where sellable_id=:product
                ), saved as (
                    select product_id,quantity_per_combo as factor,unit_code from public.quote_combo_items where quote_item_id=:item
                )
                select exists(select 1 from saved) and not exists(
                    (select * from current_items except select * from saved)
                    union all (select * from saved except select * from current_items)
                )
                """, Map.of("product", item.getProductId(), "item", item.getId()), Boolean.class);
        return Boolean.TRUE.equals(matches);
    }

    public void validateSnapshot(QuoteItem item) {
        if (!snapshotMatches(item)) throw new BusinessException(ErrorCode.QUOTE_PRICING_STALE);
    }
}
