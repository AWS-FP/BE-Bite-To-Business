
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.ProductDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductDetailRepository
    extends JpaRepository<ProductDetail, UUID> {
}
