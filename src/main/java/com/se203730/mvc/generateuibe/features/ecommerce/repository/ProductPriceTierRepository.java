
package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductPriceTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductPriceTierRepository extends JpaRepository<ProductPriceTier, UUID> {

    List<ProductPriceTier> findByProductIdOrderByMinQuantityAscIdAsc(
        UUID productId
    );

    List<ProductPriceTier> findByProductIdInOrderByMinQuantityAscIdAsc(
        Collection<UUID> productIds
    );
}
