
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Lấy toàn bộ sản phẩm PUBLISHED của brand, sắp theo tên và ID. */
    List<Product> findByBrandIdAndStatusOrderByNameViAscIdAsc(
        UUID brandId,
        String status
    );
}
