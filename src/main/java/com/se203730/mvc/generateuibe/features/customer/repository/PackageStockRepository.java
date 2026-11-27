package com.se203730.mvc.generateuibe.features.customer.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Truy vấn kho theo package: hàng trong package, hàng còn đang giữ và package quá ngày giao. */
@Repository
public class PackageStockRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PackageStockRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public record Item(UUID productId, BigDecimal quantity) {}

    public record ExpiredPackage(UUID packageId, UUID orderId, UUID customerId, String packageName) {}

    public List<Item> items(UUID packageId) {
        return jdbc.query("""
                select c.product_id, sum(pi.quantity * c.factor) as quantity
                from public.package_items pi join checkout_stock.order_components c on c.order_item_id = pi.order_item_id
                where pi.order_package_id = :packageId
                group by c.product_id order by c.product_id
                """, new MapSqlParameterSource("packageId", packageId),
                (rs, i) -> new Item(rs.getObject("product_id", UUID.class), rs.getBigDecimal("quantity")));
    }

    public void lockOrder(UUID orderId) {
        jdbc.queryForObject("select id from public.orders where id=:orderId for update",
                new MapSqlParameterSource("orderId", orderId), UUID.class);
    }

    public boolean usesComponentStock(UUID orderId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from public.order_items where order_id=:orderId and component_inventory)
                """, new MapSqlParameterSource("orderId", orderId), Boolean.class));
    }

    public BigDecimal packageMovement(UUID packageId, UUID productId, String type) {
        return jdbc.queryForObject("""
                select coalesce(sum(quantity),0) from public.inventory_movements
                where order_package_id=:packageId and product_id=:productId and movement_type=:type
                """, new MapSqlParameterSource("packageId", packageId).addValue("productId",productId)
                .addValue("type",type), BigDecimal.class);
    }

    /** Lượng của sản phẩm còn đang giữ cho đơn (đã giữ trừ đã nhả và đã xuất). */
    public BigDecimal heldQuantity(UUID orderId, UUID productId) {
        BigDecimal value = jdbc.queryForObject("""
                select coalesce(sum(case movement_type when 'RESERVE' then quantity
                    when 'RELEASE' then -quantity when 'SHIP' then -quantity else 0 end), 0)
                from public.inventory_movements where order_id = :orderId and product_id = :productId
                """, new MapSqlParameterSource("orderId", orderId).addValue("productId", productId), BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    public void addMovement(UUID productId, UUID orderId, UUID packageId, String type, BigDecimal quantity,
                            String reason, UUID actorId, Instant now) {
        jdbc.update("""
                insert into public.inventory_movements
                    (id, product_id, order_id, order_package_id, movement_type, quantity, reason, created_by, created_at)
                values (:id, :productId, :orderId, :packageId, :type, :quantity, :reason, :actor, :now)
                """, new MapSqlParameterSource("id", UUID.randomUUID())
                .addValue("productId", productId).addValue("orderId", orderId).addValue("packageId", packageId)
                .addValue("type", type).addValue("quantity", quantity).addValue("reason", reason)
                .addValue("actor", actorId).addValue("now", Timestamp.from(now)));
    }

    /** Package của đơn đã thanh toán, đã qua ngày giao mà chưa có nhân viên nhận. */
    public List<ExpiredPackage> findUnclaimedPastDeliveryDate(LocalDate today, int limit) {
        return jdbc.query("""
                select p.id, p.order_id, o.customer_id, p.package_name
                from public.order_packages p join public.orders o on o.id = p.order_id
                where o.status = 'COMPLETED' and p.cancelled_at is null and p.delivery_date < :today
                  and not exists (select 1 from public.delivery_package_claims c where c.package_id = p.id)
                order by p.delivery_date, p.id limit :limit
                """, new MapSqlParameterSource("today", today).addValue("limit", limit),
                (rs, i) -> new ExpiredPackage(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getObject("customer_id", UUID.class), rs.getString("package_name")));
    }

    /** Hủy package nếu vẫn chưa hủy và chưa có nhân viên nhận; trả về số dòng đổi. */
    public int cancelIfUnclaimed(UUID packageId, Instant now) {
        return jdbc.update("""
                update public.order_packages set cancelled_at = :now, updated_at = :now
                where id = :packageId and cancelled_at is null
                  and not exists (select 1 from public.delivery_package_claims c where c.package_id = :packageId)
                """, new MapSqlParameterSource("packageId", packageId).addValue("now", Timestamp.from(now)));
    }
}
