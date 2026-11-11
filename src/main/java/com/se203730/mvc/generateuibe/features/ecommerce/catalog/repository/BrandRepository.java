package com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    /** Danh sách thương hiệu, sắp xếp theo tên không phân biệt hoa/thường. */
    @Query("select b from Brand b order by lower(b.name) asc, b.id asc")
    List<Brand> findAllSorted();
}
