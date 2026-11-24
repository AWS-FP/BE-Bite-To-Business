
package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.CatalogSuggestion;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Set;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends
    JpaRepository<Product, UUID>,
    JpaSpecificationExecutor<Product> {

    List<Product> findByBrandIdAndStatusOrderByNameViAscIdAsc(
        UUID brandId,
        String status
    );

    @Query(value = """
        SELECT p.* FROM public.products src JOIN public.products p ON p.category_id IN (:categories)
        WHERE src.id=:id AND src.status='PUBLISHED' AND p.status='PUBLISHED' AND p.id<>src.id
        ORDER BY (src.brand_id IS NOT NULL AND p.brand_id IS NOT NULL AND p.brand_id=src.brand_id) DESC,
            (p.category_id=src.category_id) DESC, (p.unit_code=src.unit_code) DESC,
            lower(p.name_vi), p.id LIMIT 4
        """, nativeQuery = true)
    List<Product> related(@Param("id") UUID id, @Param("categories") Set<UUID> categories);

    @Query(value = """
        WITH top_hits AS (
            SELECT p.*, r.relevance FROM catalog_search.ranked(:q) r
            JOIN public.products p ON p.id=r.product_id
            ORDER BY r.relevance DESC, lower(p.name_vi), p.id LIMIT 12
        ), options AS (
            SELECT s.*, h.relevance FROM top_hits h
            LEFT JOIN public.brands b ON b.id=h.brand_id
            LEFT JOIN public.categories c ON c.id=h.category_id
                AND c.id IN (SELECT id FROM catalog_search.visible_categories)
            CROSS JOIN LATERAL (VALUES
                ('product',h.id,h.name_vi,h.name_en),
                ('brand',b.id,b.name,b.name),
                ('category',c.id,c.name_vi,c.name_en),
                ('query',CAST(NULL AS uuid),concat_ws(' ',c.name_vi,coalesce(b.name,h.unit_code)),
                    concat_ws(' ',c.name_en,coalesce(b.name,h.unit_code)))
            ) s(kind,id,vi,en) WHERE s.vi IS NOT NULL
                AND (s.kind <> 'query' OR (c.id IS NOT NULL AND greatest(length(s.vi), length(s.en)) <= 100))
        ), grouped AS (
            SELECT kind,id,vi,en,max(relevance) AS score FROM options GROUP BY kind,id,vi,en
        ), limited AS (
            SELECT *, row_number() OVER (PARTITION BY kind ORDER BY score DESC,vi,id,en) AS n
            FROM grouped
        )
        SELECT kind,id,vi AS "labelVi",en AS "labelEn" FROM limited
        WHERE n <= CASE WHEN kind='product' THEN 4 ELSE 2 END
        ORDER BY CASE kind WHEN 'product' THEN 1 WHEN 'brand' THEN 2 WHEN 'category' THEN 3 ELSE 4 END,n
        """, nativeQuery = true)
    List<CatalogSuggestion> suggestions(@Param("q") String q);
}
