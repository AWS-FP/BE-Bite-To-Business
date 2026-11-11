package com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Lấy toàn bộ danh mục active trong một query, theo thứ tự hiển thị. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    @Query("""
            select c from Category c
            where c.active = true
            order by c.sortOrder asc, c.nameVi asc, c.id asc
            """)
    List<Category> findAllActiveSorted();
}
