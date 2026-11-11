package com.se203730.mvc.generateuibe.features.ecommerce.home.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response.HomeComboItemResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response.HomeComboResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response.HomeProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Home owns product curation and combos, NOT promotion business rules. */
@Repository
@RequiredArgsConstructor
public class HomeRepository {

    private final JdbcTemplate jdbc;

    private boolean hasFeaturedTable() {
        Boolean exists = jdbc.queryForObject(
                "SELECT to_regclass('public.home_featured_products') IS NOT NULL",
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public List<HomeProductResponse> findFeaturedProducts(int limit) {
        if (!hasFeaturedTable()) {
            // No curation data configured yet. Never invent 'featured' products.
            return List.of();
        }

        String sql = """
                SELECT p.id AS product_id, p.sku, p.name_vi, p.name_en,
                       p.image_url, p.unit_code, pp.list_price,
                       CASE WHEN inv.product_id IS NULL THEN NULL
                            ELSE GREATEST(inv.on_hand_quantity - inv.reserved_quantity, 0)
                       END AS available_quantity
                FROM public.home_featured_products f
                JOIN public.products p ON p.id = f.product_id
                LEFT JOIN public.product_prices pp ON pp.product_id = p.id
                LEFT JOIN public.inventory inv ON inv.product_id = p.id
                WHERE f.is_active = TRUE
                  AND p.status = 'PUBLISHED'
                ORDER BY f.sort_order ASC, p.id ASC
                LIMIT ?
                """;

        return jdbc.query(sql, (rs, rowNum) -> new HomeProductResponse(
                rs.getObject("product_id", UUID.class),
                rs.getString("sku"),
                rs.getString("name_vi"),
                rs.getString("name_en"),
                rs.getString("image_url"),
                rs.getString("unit_code"),
                rs.getBigDecimal("list_price"),
                null, // PromotionService enriches sale data.
                rs.getBigDecimal("available_quantity"),
                null, null, null
        ), limit);
    }

    public List<HomeComboResponse> findCombos(int limit) {
        String sql = """
                SELECT p.id AS product_id, p.sku, p.name_vi, p.name_en,
                       p.image_url, c.serving_count, pp.list_price,
                       CASE WHEN inv.product_id IS NULL THEN NULL
                            ELSE GREATEST(inv.on_hand_quantity - inv.reserved_quantity, 0)
                       END AS available_quantity
                FROM public.combos c
                JOIN public.products p ON p.id = c.product_id
                LEFT JOIN public.product_prices pp ON pp.product_id = p.id
                LEFT JOIN public.inventory inv ON inv.product_id = p.id
                WHERE p.status = 'PUBLISHED'
                  AND p.unit_code = 'COMBO'
                  AND EXISTS (
                      SELECT 1 FROM public.combo_items ci
                      WHERE ci.combo_id = c.product_id
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM public.combo_items ci
                      JOIN public.products child ON child.id = ci.product_id
                      WHERE ci.combo_id = c.product_id
                        AND child.status <> 'PUBLISHED'
                  )
                ORDER BY p.created_at DESC, p.id ASC
                LIMIT ?
                """;

        List<HomeComboResponse> combos = jdbc.query(sql, (rs, rowNum) ->
                new HomeComboResponse(
                        rs.getObject("product_id", UUID.class),
                        rs.getString("sku"),
                        rs.getString("name_vi"),
                        rs.getString("name_en"),
                        rs.getString("image_url"),
                        (Integer) rs.getObject("serving_count"),
                        rs.getBigDecimal("list_price"),
                        null, // PromotionService enriches sale data.
                        rs.getBigDecimal("available_quantity"),
                        List.of()
                ), limit);

        if (combos.isEmpty()) {
            return combos;
        }

        // Load components with one query, avoiding N+1 queries.
        List<UUID> ids = combos.stream().map(HomeComboResponse::productId).toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String itemSql = """
                SELECT ci.combo_id, ci.product_id, ci.quantity,
                       p.sku, p.name_vi, p.unit_code, p.image_url
                FROM public.combo_items ci
                JOIN public.products p ON p.id = ci.product_id
                WHERE ci.combo_id IN (
                """ + placeholders + """
                )
                ORDER BY ci.combo_id ASC, p.name_vi ASC, ci.product_id ASC
                """;

        List<HomeComboItemResponse> rows = jdbc.query(itemSql, (rs, rowNum) ->
                new HomeComboItemResponse(
                        rs.getObject("combo_id", UUID.class),
                        rs.getObject("product_id", UUID.class),
                        rs.getString("sku"),
                        rs.getString("name_vi"),
                        rs.getString("unit_code"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("image_url")
                ), ids.toArray());

        Map<UUID, List<HomeComboItemResponse>> byCombo = new HashMap<>();
        for (HomeComboItemResponse item : rows) {
            byCombo.computeIfAbsent(item.comboId(), unused -> new ArrayList<>())
                    .add(item);
        }

        return combos.stream().map(combo -> new HomeComboResponse(
                combo.productId(), combo.sku(), combo.nameVi(), combo.nameEn(),
                combo.imageUrl(), combo.servingCount(), combo.listPrice(),
                combo.salePrice(), combo.availableQuantity(),
                List.copyOf(byCombo.getOrDefault(combo.productId(), List.of()))
        )).toList();
    }
}
