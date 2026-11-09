
package com.se203730.mvc.generateuibe.features.ecommerce.pricing.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.pricing.entity.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, UUID> {
}
