package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutiveProductStatusRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutivePricingRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.EcommerceExecutiveRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ExecutiveCatalogSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExecutiveProductService {
    private final EcommerceExecutiveRepository db;
    private final ExecutiveCatalogSearchRepository searchDb;
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

    @Transactional(readOnly = true)
    public EcommercePageResponse<Map<String,Object>> list(String q, String status, int page, int size) {
        // Validate query length using the existing boundary before entering search SQL.
        String query = EcommerceExecutiveRepository.find(q, 100);
        if (status != null && !status.isBlank() && !STATUSES.contains(status))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (query != null) {
            // Pass the raw term: fold_v1 handles punctuation, accents and compact SKUs.
            return searchDb.search(q.strip(), status, page, size);
        }
        var p = new MapSqlParameterSource();
        StringBuilder from = new StringBuilder(" from public.products p " +
            "left join public.product_prices pp on pp.product_id=p.id where 1=1 ");
        if (status != null && !status.isBlank()) {
            from.append(" and p.status=:status ");
            p.addValue("status",status);
        }
        return db.page("select p.id,p.sku,p.name_vi,p.name_en,p.brand_id,p.category_id,p.status,p.unit_code," +
            "p.image_url,p.vat_rate,p.min_order_quantity,p.quantity_step,p.origin," +
            "p.package_spec_vi,p.package_spec_en,p.description_vi,p.description_en," +
            "p.created_at,p.updated_at,pp.list_price,pp.sale_price,pp.sale_discount_percent " +
            from.toString() + " order by p.updated_at desc,p.id", "select count(*) " + from, p, page, size);
    }

    @Transactional
    public Map<String,Object> updateStatus(UUID id, ExecutiveProductStatusRequest req) {
        String status = req.status().strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p = new MapSqlParameterSource("id", id);
        Map<String,Object> product = db.one("select id, status, sku, name_vi, name_en, unit_code " +
            "from public.products where id=:id for update",p);
        if (status.equals("PUBLISHED")) {
            Integer priced = db.queryForObject("select count(*) from public.product_prices " +
                "where product_id=:id and list_price is not null and exists " +
                "(select 1 from public.products where id=:id and vat_rate is not null)", p, Integer.class);
            if (priced == null || priced == 0 || product.get("nameVi") == null || product.get("nameEn") == null)
                throw new BusinessException(ErrorCode.CONFLICT);
        }
        db.update("update public.products set status=:status,updated_at=now() where id=:id",
            p.addValue("status", status));
        return db.one("select id,sku,name_vi,name_en,status,updated_at from public.products where id=:id",p);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> pricing(UUID id) {
        var p = new MapSqlParameterSource("id",id);
        Map<String,Object> item = db.one("select p.id as product_id,p.sku,p.unit_code,p.min_order_quantity," +
            "p.quantity_step,pp.list_price,pp.sale_price,pp.sale_discount_percent " +
            "from public.products p left join public.product_prices pp on pp.product_id=p.id where p.id=:id",p);
        item.put("priceTiers",db.list("select id,min_quantity,max_quantity,unit_price " +
            "from public.product_price_tiers where product_id=:id order by min_quantity",p));
        return item;
    }

    @Transactional
    public Map<String,Object> putPricing(UUID id, ExecutivePricingRequest req) {
        var p = new MapSqlParameterSource("id",id);
        var product = db.one("select id,min_order_quantity,quantity_step from public.products where id=:id for update",p);
        BigDecimal moq = (BigDecimal)product.get("minOrderQuantity");
        BigDecimal step = (BigDecimal)product.get("quantityStep");
        // E-commerce executives manage promotional and tier prices only.
        // Lock the current authoritative list price and reject a crafted API payload
        // attempting to change it (a read-only FE input is not authorization).
        BigDecimal existingListPrice = db.lockListPrice(id);
        if (existingListPrice == null) throw new BusinessException(ErrorCode.CONFLICT);
        if (req.listPrice().compareTo(existingListPrice) != 0)
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        if (req.salePrice() != null && (req.salePrice().compareTo(existingListPrice) >= 0 ||
            req.salePrice().stripTrailingZeros().scale() > 0))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (req.salePrice()!=null && req.saleDiscountPercent()!=null)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (req.tiers().size() > 100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var tiers = new ArrayList<>(req.tiers());
        tiers.sort(Comparator.comparing(ExecutivePricingRequest.Tier::minQuantity));
        BigDecimal previousMax = null;
        for (int i=0;i<tiers.size();i++) {
            var t=tiers.get(i);
            if (t.minQuantity().compareTo(moq)<0 || (previousMax!=null && t.minQuantity().compareTo(previousMax)<=0)
                || (i>0 && previousMax==null) || t.unitPrice().stripTrailingZeros().scale()>0
                || (t.maxQuantity()!=null && t.maxQuantity().compareTo(t.minQuantity())<0)
                || (t.minQuantity().subtract(moq).remainder(step).compareTo(BigDecimal.ZERO)!=0))
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            previousMax=t.maxQuantity();
        }
        db.update("update public.product_prices set sale_price=:sale," +
                "sale_discount_percent=:percent,updated_at=now() where product_id=:id",
            p.addValue("sale",req.salePrice()).addValue("percent",req.saleDiscountPercent()));
        // Preserve IDs that quote_items/order_items reference. Never delete all tiers and
        // reinsert: PostgreSQL's FK to price_tier_id would reject that for sold/quoted products.
        var existingRows = db.list("select id from public.product_price_tiers " +
            "where product_id=:id for update", p);
        Set<UUID> existingIds = new HashSet<>();
        for (var row : existingRows) existingIds.add((UUID) row.get("id"));
        Set<UUID> keptIds = new HashSet<>();
        for (var tier : tiers) {
            UUID tierId = tier.id();
            if (tierId != null && (!existingIds.contains(tierId) || !keptIds.add(tierId)))
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            var tp = new MapSqlParameterSource("id", id)
                .addValue("tierId", tierId == null ? UUID.randomUUID() : tierId)
                .addValue("min", tier.minQuantity())
                .addValue("max", tier.maxQuantity())
                .addValue("unit", tier.unitPrice());
            if (tierId == null) {
                db.update("insert into public.product_price_tiers " +
                    "(id,product_id,min_quantity,max_quantity,unit_price) " +
                    "values(:tierId,:id,:min,:max,:unit)", tp);
            } else {
                db.update("update public.product_price_tiers set " +
                    "min_quantity=:min,max_quantity=:max,unit_price=:unit,updated_at=now() " +
                    "where product_id=:id and id=:tierId", tp);
            }
        }
        Set<UUID> removed = new HashSet<>(existingIds);
        removed.removeAll(keptIds);
        if (!removed.isEmpty()) {
            var ids = new MapSqlParameterSource("ids", removed);
            Long references = db.queryForObject(
                "select (select count(*) from public.quote_items where price_tier_id in (:ids)) + " +
                    "(select count(*) from public.order_items where price_tier_id in (:ids))", ids, Long.class);
            if (references != null && references > 0)
                throw new BusinessException(ErrorCode.CONFLICT);
            db.update("delete from public.product_price_tiers " +
                    "where product_id=:id and id in (:ids)",
                new MapSqlParameterSource("id", id).addValue("ids", removed));
        }
        return pricing(id);
    }
}
