
package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.CatalogSuggestion;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends
    JpaRepository<Product, UUID>,
    JpaSpecificationExecutor<Product> {

    List<Product> findByBrandIdAndStatusOrderByNameViAscIdAsc(
        UUID brandId,
        String status
    );

    // Only recommend products from the SAME leaf category, not unrelated siblings.
    @Query(value = """
        SELECT p.* FROM public.products src
        JOIN public.products p ON p.category_id = src.category_id
        WHERE src.id = :id AND src.status = 'PUBLISHED'
          AND p.status = 'PUBLISHED' AND p.id <> src.id
        ORDER BY (p.brand_id IS NOT DISTINCT FROM src.brand_id) DESC,
                 (p.unit_code = src.unit_code) DESC,
                 lower(p.name_vi), p.id
        LIMIT 4
        """, nativeQuery = true)
    List<Product> related(@Param("id") UUID id);

    // Brand and category suggestions must match the typed query DIRECTLY, not
    // merely be attached to a loosely matching product (old source of noisy hints).
    @Query(value = """
        WITH product_hits AS (
            SELECT p.id, p.name_vi, p.name_en, p.image_url, r.relevance
            FROM catalog_search.ranked(:q) r
            JOIN public.products p ON p.id = r.product_id
            WHERE r.relevance >= CASE WHEN length(catalog_search.fold_v1(:q)) <= 3 THEN 35 ELSE 14 END
            ORDER BY r.relevance DESC, lower(p.name_vi), p.id
            LIMIT 5
        ), brand_hits AS (
            SELECT b.id, b.name
            FROM public.brands b
            WHERE position(catalog_search.fold_v1(:q) IN catalog_search.fold_v1(b.name)) > 0
              AND catalog_search.fold_v1(:q) <> ''
            ORDER BY CASE WHEN catalog_search.fold_v1(b.name) = catalog_search.fold_v1(:q) THEN 0 ELSE 1 END,
                     lower(b.name), b.id
            LIMIT 2
        ), category_hits AS (
            SELECT c.id, c.name_vi, c.name_en
            FROM public.categories c
            WHERE c.id IN (SELECT id FROM catalog_search.visible_categories)
              AND (position(catalog_search.fold_v1(:q) IN catalog_search.fold_v1(c.name_vi)) > 0
                   OR position(catalog_search.fold_v1(:q) IN catalog_search.fold_v1(c.name_en)) > 0)
              AND catalog_search.fold_v1(:q) <> ''
            ORDER BY lower(c.name_vi), c.id
            LIMIT 2
        )
        SELECT kind, id, "labelVi", "labelEn", "imageUrl"
        FROM (
            SELECT 'product' AS kind, id, name_vi AS "labelVi", name_en AS "labelEn",
                   image_url AS "imageUrl", 0 AS sort_group, relevance
            FROM product_hits
            UNION ALL
            SELECT 'brand', id, name, name, NULL, 1, 0::double precision FROM brand_hits
            UNION ALL
            SELECT 'category', id, name_vi, name_en, NULL, 2, 0::double precision
            FROM category_hits
        ) options
        ORDER BY sort_group, relevance DESC, lower("labelVi"), id
        """, nativeQuery = true)
    List<CatalogSuggestion> suggestions(@Param("q") String q);
}
