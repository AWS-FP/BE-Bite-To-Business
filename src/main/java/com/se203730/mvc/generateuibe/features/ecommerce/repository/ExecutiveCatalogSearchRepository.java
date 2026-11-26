package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * Read-only executive search. Reuses the Customer's V51 ranked search for PUBLISHED
 * products, and performs conservative normalized matching on DRAFT/ARCHIVED products.
 * Never exposes unpublished products through the public catalog endpoint.
 */
@Repository
@RequiredArgsConstructor
public class ExecutiveCatalogSearchRepository {
    private final EcommerceExecutiveRepository db;

    private static final String SEARCH_FROM = """
        FROM public.products p
        LEFT JOIN public.product_prices pp ON pp.product_id = p.id
        LEFT JOIN public.brands b ON b.id = p.brand_id
        LEFT JOIN public.categories c ON c.id = p.category_id
        LEFT JOIN catalog_search.ranked(:term, CAST(NULL AS uuid), CAST(NULL AS uuid[])) r
            ON r.product_id = p.id
        CROSS JOIN LATERAL (
            SELECT catalog_search.fold_v1(:term) AS q,
                   catalog_search.fold_v1(p.sku) AS sku_key,
                   catalog_search.fold_v1(p.name_vi || ' ' || p.name_en) AS name_key,
                   catalog_search.fold_v1(coalesce(b.name, '')) AS brand_key,
                   catalog_search.fold_v1(coalesce(c.name_vi, '') || ' ' || coalesce(c.name_en, '')) AS category_key
        ) d
        CROSS JOIN LATERAL (
            SELECT CASE
                -- For published products, obey precisely the same V51 relevance gate as Customer.
                WHEN p.status = 'PUBLISHED' THEN coalesce(r.relevance, 0)
                -- Unpublished products are management-only. Match their names/SKUs
                -- conservatively, without fuzzy expansion or unrelated suggestions.
                WHEN lower(p.sku) = lower(trim(:term)) THEN 150
                WHEN d.q <> '' AND d.sku_key = d.q THEN 145
                WHEN d.q <> '' AND replace(d.sku_key, ' ', '') = replace(d.q, ' ', '') THEN 140
                WHEN lower(p.name_vi) = lower(trim(:term)) OR lower(p.name_en) = lower(trim(:term)) THEN 135
                WHEN d.q <> '' AND (catalog_search.fold_v1(p.name_vi) = d.q
                    OR catalog_search.fold_v1(p.name_en) = d.q) THEN 125
                WHEN d.q <> '' AND position(' ' || d.q IN ' ' || d.name_key) > 0 THEN 110
                WHEN d.q <> '' AND NOT EXISTS (
                    SELECT 1 FROM unnest(string_to_array(d.q, ' ')) AS token
                    WHERE token <> '' AND position(' ' || token IN ' ' || d.name_key) = 0
                ) THEN 90
                WHEN d.q <> '' AND (d.brand_key = d.q OR d.category_key = d.q) THEN 60
                ELSE 0
            END AS relevance
        ) relevance
        WHERE relevance.relevance > 0
        """;

    /** Ranking applies before LIMIT/OFFSET, with stable ties across pages. */
    public EcommercePageResponse<Map<String, Object>> search(
        String rawQuery, String status, int page, int size
    ) {
        var params = new MapSqlParameterSource("term", rawQuery);
        String from = SEARCH_FROM;
        if (status != null && !status.isBlank()) {
            from += " AND p.status = :status ";
            params.addValue("status", status);
        }
        String select = """
            SELECT p.id,p.sku,p.name_vi,p.name_en,p.brand_id,p.category_id,p.status,p.unit_code,
                   p.image_url,p.vat_rate,p.min_order_quantity,p.quantity_step,p.origin,
                   p.package_spec_vi,p.package_spec_en,p.description_vi,p.description_en,
                   p.created_at,p.updated_at,pp.list_price,pp.sale_price,pp.sale_discount_percent
            """;
        return db.page(select + from + " ORDER BY relevance.relevance DESC, p.id ASC",
            "SELECT count(*) " + from, params, page, size);
    }
}
