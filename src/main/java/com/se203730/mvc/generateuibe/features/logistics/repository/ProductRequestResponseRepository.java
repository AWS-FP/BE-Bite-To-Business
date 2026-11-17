package com.se203730.mvc.generateuibe.features.logistics.repository;

import com.se203730.mvc.generateuibe.features.logistics.entity.ProductRequestResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductRequestResponseRepository extends JpaRepository<ProductRequestResponse, UUID> {
    interface ProductSnapshot {
        String getName();
        String getUnit();
        Instant getAddedAt();
        BigDecimal getPrice();
        BigDecimal getAvailable();
    }

    @Query(value = """
            select p.name_vi as name, p.unit_code as unit, p.created_at as "addedAt",
                   prices.list_price as price, inventory.available_quantity as available
            from public.products p
            join public.product_prices prices on prices.product_id = p.id
            join public.inventory inventory on inventory.product_id = p.id
            where p.id = :id and p.status = 'PUBLISHED'
            """, nativeQuery = true)
    Optional<ProductSnapshot> findProductSnapshot(@Param("id") UUID id);
}
