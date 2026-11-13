package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface QuoteCatalogRepository extends JpaRepository<QuoteItem, UUID> {
    interface CatalogPrice {
        UUID getProductId();
        String getSku();
        String getNameVi();
        String getNameEn();
        String getUnitCode();
        BigDecimal getMinQuantity();
        BigDecimal getQuantityStep();
        BigDecimal getListPrice();
        BigDecimal getSalePrice();
        BigDecimal getSaleDiscountPercent();
        BigDecimal getVatRate();
        UUID getTierId();
        BigDecimal getTierPrice();
        Boolean getPromotionActive();
    }

    @Query(value = """
        select p.id as "productId", p.sku as sku, p.name_vi as "nameVi", p.name_en as "nameEn",
            p.unit_code as "unitCode", p.min_order_quantity as "minQuantity", p.quantity_step as "quantityStep",
            pp.list_price as "listPrice", pp.sale_price as "salePrice", pp.sale_discount_percent as "saleDiscountPercent",
            p.vat_rate as "vatRate",
            tier.id as "tierId", tier.unit_price as "tierPrice",
            coalesce(promo.status = 'PUBLISHED' and promo.starts_at <= clock_timestamp()
                and promo.ends_at > clock_timestamp(), false) as "promotionActive"
        from public.products p
        join public.product_prices pp on pp.product_id = p.id
        left join public.promotions promo on promo.id = p.promotion_id
        left join lateral (
            select id, unit_price from public.product_price_tiers
            where product_id = p.id and min_quantity <= :quantity
                and (max_quantity is null or max_quantity >= :quantity)
            order by min_quantity desc limit 1
        ) tier on true
        where p.id = :productId and p.status = 'PUBLISHED'
        """, nativeQuery = true)
    Optional<CatalogPrice> findPrice(@Param("productId") UUID productId, @Param("quantity") BigDecimal quantity);
}
