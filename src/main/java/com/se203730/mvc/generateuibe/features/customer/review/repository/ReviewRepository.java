package com.se203730.mvc.generateuibe.features.customer.review.repository;

import com.se203730.mvc.generateuibe.features.customer.review.dto.response.ReviewImageResponse;
import com.se203730.mvc.generateuibe.features.customer.review.dto.response.ReviewPageResponse;
import com.se203730.mvc.generateuibe.features.customer.review.dto.response.ReviewResponse;
import com.se203730.mvc.generateuibe.features.customer.review.dto.response.ReviewSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public boolean isPublished(UUID productId) {
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM public.products WHERE id = ? AND status = 'PUBLISHED')",
            Boolean.class, productId);
        return Boolean.TRUE.equals(exists);
    }

    public boolean hasActiveReview(UUID customerId, UUID productId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM public.product_reviews
                              WHERE customer_id = ? AND product_id = ? AND deleted_at IS NULL)
                """, Boolean.class, customerId, productId);
        return Boolean.TRUE.equals(exists);
    }

    public UUID insert(UUID customerId, UUID productId, BigDecimal rating, String content) {
        return jdbc.queryForObject("""
                INSERT INTO public.product_reviews(id, product_id, customer_id, rating, content)
                VALUES (gen_random_uuid(), ?, ?, ?, ?) RETURNING id
                """, UUID.class, productId, customerId, rating, content);
    }

    public void lockOwnedReview(UUID reviewId, UUID customerId) {
        List<UUID> rows = jdbc.query("""
                SELECT id FROM public.product_reviews
                WHERE id = ? AND customer_id = ? AND deleted_at IS NULL FOR UPDATE
                """, (rs, row) -> rs.getObject("id", UUID.class), reviewId, customerId);
        if (rows.isEmpty()) throw new com.se203730.mvc.generateuibe.common.exception.BusinessException(
            com.se203730.mvc.generateuibe.common.exception.ErrorCode.RESOURCE_NOT_FOUND);
    }

    public boolean isOwner(UUID reviewId, UUID customerId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM public.product_reviews
                              WHERE id = ? AND customer_id = ? AND deleted_at IS NULL)
                """, Boolean.class, reviewId, customerId);
        return Boolean.TRUE.equals(exists);
    }

    public int update(UUID reviewId, UUID customerId, BigDecimal rating, String content) {
        return jdbc.update("""
                UPDATE public.product_reviews SET rating = ?, content = ?, updated_at = now()
                WHERE id = ? AND customer_id = ? AND deleted_at IS NULL
                """, rating, content, reviewId, customerId);
    }

    public int softDelete(UUID reviewId, UUID customerId) {
        return jdbc.update("""
                UPDATE public.product_reviews
                SET deleted_at = now(), deleted_by = ?, updated_at = now()
                WHERE id = ? AND customer_id = ? AND deleted_at IS NULL
                """, customerId, reviewId, customerId);
    }

    public int countImages(UUID reviewId) {
        Integer count = jdbc.queryForObject(
            "SELECT jsonb_array_length(images) FROM public.product_reviews WHERE id = ?", Integer.class, reviewId);
        return count == null ? 0 : count;
    }

    public int nextSortOrder(UUID reviewId) {
        Integer number = jdbc.queryForObject(
            "SELECT COALESCE(MAX((image->>'sort_order')::int), -1) + 1 FROM public.product_reviews r CROSS JOIN LATERAL jsonb_array_elements(r.images) image WHERE r.id = ?",
            Integer.class, reviewId);
        return number == null ? 0 : number;
    }

    public void insertImage(UUID reviewId, String url, String publicId, int sortOrder) {
        jdbc.update("""
                UPDATE public.product_reviews SET images = images || jsonb_build_array(jsonb_build_object(
                    'id', gen_random_uuid(), 'image_url', CAST(? AS text), 'cloudinary_public_id', CAST(? AS text),
                    'sort_order', CAST(? AS integer), 'created_at', now())) WHERE id = ?
                """, url, publicId, sortOrder, reviewId);
    }

    public record ImageForDeletion(String publicId) {}

    public ImageForDeletion findImage(UUID reviewId, UUID imageId) {
        List<ImageForDeletion> values = jdbc.query("""
                SELECT image->>'cloudinary_public_id' AS cloudinary_public_id
                FROM public.product_reviews r CROSS JOIN LATERAL jsonb_array_elements(r.images) image
                WHERE r.id = ? AND image->>'id' = CAST(? AS text)
                """, (rs, row) -> new ImageForDeletion(rs.getString("cloudinary_public_id")),
            reviewId, imageId);
        return values.isEmpty() ? null : values.getFirst();
    }

    public int deleteImage(UUID reviewId, UUID imageId) {
        return jdbc.update("""
                UPDATE public.product_reviews r SET images = COALESCE(
                    (SELECT jsonb_agg(image ORDER BY position) FROM jsonb_array_elements(r.images)
                     WITH ORDINALITY AS entries(image, position) WHERE image->>'id' <> CAST(? AS text)), '[]'::jsonb)
                WHERE r.id = ? AND EXISTS (SELECT 1 FROM jsonb_array_elements(r.images) image
                                            WHERE image->>'id' = CAST(? AS text))
                """, imageId, reviewId, imageId);
    }

    public ReviewResponse findReview(UUID reviewId) {
        List<ReviewResponse> rows = jdbc.query("""
                SELECT r.id, r.product_id, r.customer_id,
                       CONCAT_WS(' ', u.first_name, u.last_name) AS reviewer_name,
                       r.rating, r.content, r.created_at, r.updated_at
                FROM public.product_reviews r
                JOIN public.users u ON u.id = r.customer_id
                WHERE r.id = ? AND r.deleted_at IS NULL
                """, (rs, row) -> mapReview(rs), reviewId);
        if (rows.isEmpty()) return null;
        ReviewResponse result = rows.getFirst();
        return result.withImages(imagesForReviews(List.of(reviewId)).getOrDefault(reviewId, List.of()));
    }

    /** Review count and average rating for many products in one GROUP BY (catalog cards). */
    public List<ReviewSummaryResponse> summaries(List<UUID> productIds) {
        if (productIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(productIds.size(), "?"));
        Map<UUID, ReviewSummaryResponse> found = new java.util.HashMap<>();
        jdbc.query("""
                SELECT product_id, COUNT(*) AS review_count, AVG(rating) AS average_rating
                FROM public.product_reviews
                WHERE deleted_at IS NULL AND product_id IN (%s)
                GROUP BY product_id
                """.formatted(placeholders), rs -> {
            UUID id = rs.getObject("product_id", UUID.class);
            BigDecimal average = rs.getBigDecimal("average_rating");
            found.put(id, new ReviewSummaryResponse(id, rs.getLong("review_count"),
                    average == null ? null : average.setScale(2, RoundingMode.HALF_UP)));
        }, productIds.toArray());
        // Products without reviews still get a row (0 reviews) so the client can stop asking.
        return productIds.stream()
                .map(id -> found.getOrDefault(id, new ReviewSummaryResponse(id, 0, null)))
                .toList();
    }

    public ReviewPageResponse list(UUID productId, int page, int size) {
        Map<String, Object> stats = jdbc.queryForMap("""
                SELECT COUNT(*) AS review_count, AVG(rating) AS average_rating
                FROM public.product_reviews WHERE product_id = ? AND deleted_at IS NULL
                """, productId);
        long count = ((Number) stats.get("review_count")).longValue();
        BigDecimal average = stats.get("average_rating") == null ? BigDecimal.ZERO
            : new BigDecimal(stats.get("average_rating").toString())
            .setScale(2, RoundingMode.HALF_UP);
        List<ReviewResponse> rows = jdbc.query("""
                SELECT r.id, r.product_id, r.customer_id,
                       CONCAT_WS(' ', u.first_name, u.last_name) AS reviewer_name,
                       r.rating, r.content, r.created_at, r.updated_at
                FROM public.product_reviews r
                JOIN public.users u ON u.id = r.customer_id
                WHERE r.product_id = ? AND r.deleted_at IS NULL
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> mapReview(rs), productId, size, (long) page * size);
        Map<UUID, List<ReviewImageResponse>> images = imagesForReviews(rows.stream().map(ReviewResponse::id).toList());
        List<ReviewResponse> full = rows.stream()
            .map(row -> row.withImages(images.getOrDefault(row.id(), List.of()))).toList();
        return new ReviewPageResponse(full, page, size, count,
            (int) ((count + size - 1) / size), average);
    }

    public com.se203730.mvc.generateuibe.features.customer.review.dto.response.CustomerReviewsPageResponse
    listByCustomer(UUID customerId, int page, int size) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.product_reviews
                WHERE customer_id = ? AND deleted_at IS NULL
                """, Long.class, customerId);
        long total = count == null ? 0 : count;
        List<ReviewResponse> rows = jdbc.query("""
                SELECT r.id, r.product_id, r.customer_id,
                       CONCAT_WS(' ', u.first_name, u.last_name) AS reviewer_name,
                       r.rating, r.content, r.created_at, r.updated_at
                FROM public.product_reviews r
                JOIN public.users u ON u.id = r.customer_id
                WHERE r.customer_id = ? AND r.deleted_at IS NULL
                ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> mapReview(rs), customerId, size, (long) page * size);
        Map<UUID, List<ReviewImageResponse>> images = imagesForReviews(
            rows.stream().map(ReviewResponse::id).toList());
        List<ReviewResponse> full = rows.stream()
            .map(row -> row.withImages(images.getOrDefault(row.id(), List.of()))).toList();
        return new com.se203730.mvc.generateuibe.features.customer.review.dto.response.CustomerReviewsPageResponse(
            full, page, size, total, (int) ((total + size - 1) / size));
    }

    private ReviewResponse mapReview(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReviewResponse(rs.getObject("id", UUID.class),
            rs.getObject("product_id", UUID.class), rs.getObject("customer_id", UUID.class),
            rs.getString("reviewer_name"), rs.getBigDecimal("rating"), rs.getString("content"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
            List.of());
    }

    private Map<UUID, List<ReviewImageResponse>> imagesForReviews(Collection<UUID> reviewIds) {
        if (reviewIds.isEmpty()) return Map.of();
        var args = new MapSqlParameterSource("ids", reviewIds);
        Map<UUID, List<ReviewImageResponse>> result = new LinkedHashMap<>();
        namedJdbc.query("""
                SELECT r.id AS review_id, (image->>'id')::uuid AS id, image->>'image_url' AS image_url,
                       (image->>'sort_order')::integer AS sort_order
                FROM public.product_reviews r CROSS JOIN LATERAL jsonb_array_elements(r.images) image
                WHERE r.id IN (:ids)
                ORDER BY r.id, (image->>'sort_order')::integer, (image->>'id')::uuid
                """, args, rs -> {
            UUID id = rs.getObject("review_id", UUID.class);
            result.computeIfAbsent(id, key -> new ArrayList<>())
                .add(new ReviewImageResponse(rs.getObject("id", UUID.class),
                    rs.getString("image_url"), rs.getInt("sort_order")));
        });
        return result;
    }
}
