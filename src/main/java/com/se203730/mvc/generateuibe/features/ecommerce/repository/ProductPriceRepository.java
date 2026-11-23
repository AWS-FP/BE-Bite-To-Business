
package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    // Chỉ áp giá sale khi promotion đang có hiệu lực.
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM public.products p
                JOIN public.promotions promo ON EXISTS (
                    SELECT 1 FROM public.promotion_products link
                    WHERE link.product_id=p.id AND link.promotion_id=promo.id)
                WHERE p.id = :productId
                  AND promo.status = 'PUBLISHED'
                  AND promo.starts_at <= CURRENT_TIMESTAMP
                  AND promo.ends_at > CURRENT_TIMESTAMP
            )
            """, nativeQuery = true)
    boolean hasActiveSalePromotion(@Param("productId") UUID productId);

    // Bản theo lô của hasActiveSalePromotion cho trang danh mục.
    @Query(value = """
            SELECT DISTINCT p.id
            FROM public.products p
            JOIN public.promotions promo ON EXISTS (
                    SELECT 1 FROM public.promotion_products link
                    WHERE link.product_id=p.id AND link.promotion_id=promo.id)
            WHERE p.id IN (:productIds)
              AND promo.status = 'PUBLISHED'
              AND promo.starts_at <= CURRENT_TIMESTAMP
              AND promo.ends_at > CURRENT_TIMESTAMP
            """, nativeQuery = true)
    List<UUID> findIdsWithActiveSalePromotion(@Param("productIds") Collection<UUID> productIds);
}
