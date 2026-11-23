package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.FavoriteProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FavoriteRepository {
    private final JdbcTemplate jdbc;

    public boolean isPublished(UUID productId) {
        Boolean result = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM public.products WHERE id = ? AND status = 'PUBLISHED')",
                Boolean.class, productId);
        return Boolean.TRUE.equals(result);
    }

    /** Which of these products the customer has favorited, in one query (catalog cards). */
    public java.util.List<UUID> favoritedAmong(UUID customerId, java.util.List<UUID> productIds) {
        if (productIds.isEmpty()) return java.util.List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(productIds.size(), "?"));
        Object[] args = new Object[productIds.size() + 1];
        args[0] = customerId;
        for (int i = 0; i < productIds.size(); i++) args[i + 1] = productIds.get(i);
        return jdbc.queryForList(
                "SELECT product_id FROM public.product_favorites WHERE customer_id = ? AND product_id IN ("
                        + placeholders + ")",
                UUID.class, args);
    }

    public boolean isFavorite(UUID customerId, UUID productId) {
        Boolean result = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM public.product_favorites WHERE customer_id = ? AND product_id = ?)",
                Boolean.class, customerId, productId);
        return Boolean.TRUE.equals(result);
    }

    public void add(UUID customerId, UUID productId) {
        jdbc.update("""
                INSERT INTO public.product_favorites(customer_id, product_id)
                VALUES (?, ?) ON CONFLICT (customer_id, product_id) DO NOTHING
                """, customerId, productId);
    }

    public void remove(UUID customerId, UUID productId) {
        jdbc.update("DELETE FROM public.product_favorites WHERE customer_id = ? AND product_id = ?",
                customerId, productId);
    }

    public PageResponse<FavoriteProductResponse> page(UUID customerId, int page, int size) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.product_favorites f
                JOIN public.products p ON p.id = f.product_id
                WHERE f.customer_id = ? AND p.status = 'PUBLISHED'
                """, Long.class, customerId);
        var rows = jdbc.query("""
                SELECT p.id, p.sku, p.name_vi, p.name_en, p.image_url,
                       pp.list_price, f.created_at
                FROM public.product_favorites f
                JOIN public.products p ON p.id = f.product_id
                LEFT JOIN public.product_prices pp ON pp.product_id = p.id
                WHERE f.customer_id = ? AND p.status = 'PUBLISHED'
                ORDER BY f.created_at DESC, p.id DESC
                LIMIT ? OFFSET ?
                """, (rs, number) -> new FavoriteProductResponse(
                rs.getObject("id", UUID.class), rs.getString("sku"),
                rs.getString("name_vi"), rs.getString("name_en"),
                rs.getString("image_url"), rs.getBigDecimal("list_price"),
                rs.getTimestamp("created_at").toInstant()),
                customerId, size, (long) page * size);
        long count = total == null ? 0 : total;
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }
}
