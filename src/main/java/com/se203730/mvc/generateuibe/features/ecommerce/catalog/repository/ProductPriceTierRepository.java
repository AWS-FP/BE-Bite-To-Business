
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.ProductPriceTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductPriceTierRepository extends JpaRepository<ProductPriceTier, UUID> {

    List<ProductPriceTier> findByProductIdOrderByMinQuantityAscIdAsc(
        UUID productId
    );
}
