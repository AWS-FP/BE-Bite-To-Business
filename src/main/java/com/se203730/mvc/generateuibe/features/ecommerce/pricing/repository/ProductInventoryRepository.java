
package com.se203730.mvc.generateuibe.features.ecommerce.pricing.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.pricing.entity.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, UUID> {
    @Modifying
    @Query(value = """
            update public.inventory set reserved_quantity = reserved_quantity + :quantity, updated_at = :now
            where product_id = :productId and available_quantity >= :quantity
            """, nativeQuery = true)
    int reserve(@Param("productId") UUID productId, @Param("quantity") BigDecimal quantity,
                @Param("now") Instant now);
    @Modifying
    @Query(value = """
            update public.inventory set reserved_quantity = reserved_quantity - :quantity, updated_at = :now
            where product_id = :productId and reserved_quantity >= :quantity
            """, nativeQuery = true)
    int release(@Param("productId") UUID productId, @Param("quantity") BigDecimal quantity,
                @Param("now") Instant now);
}
