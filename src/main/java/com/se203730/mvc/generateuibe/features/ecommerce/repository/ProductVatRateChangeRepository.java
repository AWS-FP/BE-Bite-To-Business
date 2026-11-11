package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductVatRateChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductVatRateChangeRepository extends JpaRepository<ProductVatRateChange, UUID> {
}
