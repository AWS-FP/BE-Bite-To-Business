
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    // Chỉ áp giá sale khi promotion đang có hiệu lực.
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM public.products p
                JOIN public.promotions promo ON promo.id = p.promotion_id
                WHERE p.id = :productId
                  AND promo.status = 'PUBLISHED'
                  AND promo.starts_at <= CURRENT_TIMESTAMP
                  AND promo.ends_at > CURRENT_TIMESTAMP
            )
            """, nativeQuery = true)
    boolean hasActiveSalePromotion(@Param("productId") UUID productId);
}
