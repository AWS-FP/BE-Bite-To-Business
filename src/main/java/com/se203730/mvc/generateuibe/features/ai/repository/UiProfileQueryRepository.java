package com.se203730.mvc.generateuibe.features.ai.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn tổng hợp trên bảng ui_interaction_events (bảng riêng của tính năng AI) để dựng hồ sơ thói quen giao
 * diện của một khách. Chỉ đọc sự kiện của đúng người đang yêu cầu: user_id (Customer) và/hoặc anon_id (Guest,
 * ID ẩn danh do trình duyệt sinh). Mọi điều kiện lọc là tham số, không ghép chuỗi từ input.
 */
@Repository
public class UiProfileQueryRepository {
    /** Lọc theo người dùng; tham số null thì nhánh đó không khớp gì. */
    private static final String ACTOR = """
            ((CAST(:userId AS uuid) IS NOT NULL AND e.user_id = CAST(:userId AS uuid))
              OR (CAST(:anonId AS text) IS NOT NULL AND e.anon_id = CAST(:anonId AS text)))
            AND e.occurred_at >= :since
            """;
    private static final String PRODUCT_ROUTE = "'^/products/[0-9a-fA-F-]{36}$'";

    private final NamedParameterJdbcTemplate jdbc;

    public UiProfileQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Số lần xem trang chi tiết, lần xem cuối và thời gian ở lại trung bình của từng sản phẩm. */
    public record ProductViews(UUID productId, long views, Instant lastViewedAt, Long averageDwellMs) {
    }

    /** Đếm theo một khóa (ID sản phẩm, loại component, từ khóa…). */
    public record Count(String key, long count) {
    }

    /** Số lần component xuất hiện trong giao diện AI dựng và số lần được bấm. */
    public record ComponentUse(String type, long impressions, long actions) {
    }

    public long eventCount(UUID userId, String anonId, Instant since) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM public.ui_interaction_events e WHERE " + ACTOR,
                params(userId, anonId, since), Long.class);
        return count == null ? 0 : count;
    }

    public List<ProductViews> productViews(UUID userId, String anonId, Instant since, int limit) {
        String sql = """
                SELECT CAST(substring(e.route FROM 11) AS uuid) AS product_id,
                       COUNT(*) FILTER (WHERE e.event_type = 'page_view') AS views,
                       MAX(e.occurred_at) FILTER (WHERE e.event_type = 'page_view') AS last_viewed_at,
                       AVG(CAST(e.value ->> 'dwellMs' AS bigint)) FILTER (WHERE e.event_type = 'page_leave') AS dwell
                FROM public.ui_interaction_events e
                WHERE e.event_type IN ('page_view', 'page_leave') AND e.route ~ %s AND %s
                GROUP BY 1
                HAVING COUNT(*) FILTER (WHERE e.event_type = 'page_view') > 0
                ORDER BY last_viewed_at DESC
                LIMIT :limit
                """.formatted(PRODUCT_ROUTE, ACTOR);
        return jdbc.query(sql, params(userId, anonId, since).addValue("limit", limit), (rs, row) -> {
            Timestamp last = rs.getTimestamp("last_viewed_at");
            double dwell = rs.getDouble("dwell");
            Long averageDwell = rs.wasNull() ? null : Math.round(dwell);
            return new ProductViews(rs.getObject("product_id", UUID.class), rs.getLong("views"),
                    last == null ? null : last.toInstant(), averageDwell);
        });
    }

    /** Đếm sự kiện có target là ID sản phẩm: quick_back (back nhanh) hoặc add_to_cart. */
    public List<Count> productTargets(UUID userId, String anonId, Instant since, String eventType) {
        String sql = """
                SELECT e.target AS key, COUNT(*) AS count
                FROM public.ui_interaction_events e
                WHERE e.event_type = :eventType AND e.target ~ '^[0-9a-fA-F-]{36}$' AND %s
                GROUP BY e.target ORDER BY count DESC LIMIT 20
                """.formatted(ACTOR);
        return jdbc.query(sql, params(userId, anonId, since).addValue("eventType", eventType),
                (rs, row) -> new Count(rs.getString("key"), rs.getLong("count")));
    }

    /** Hiển thị (a2ui_render liệt kê loại component) so với lần bấm (a2ui_action ghi componentType). */
    public List<ComponentUse> componentUse(UUID userId, String anonId, Instant since) {
        String sql = """
                WITH shown AS (
                    SELECT c.type, COUNT(*) AS impressions
                    FROM public.ui_interaction_events e
                    CROSS JOIN LATERAL jsonb_array_elements_text(
                        CASE WHEN jsonb_typeof(e.value -> 'components') = 'array'
                             THEN e.value -> 'components' ELSE '[]'::jsonb END) AS c(type)
                    WHERE e.event_type = 'a2ui_render' AND %1$s
                    GROUP BY c.type
                ), used AS (
                    SELECT e.value ->> 'componentType' AS type, COUNT(*) AS actions
                    FROM public.ui_interaction_events e
                    WHERE e.event_type = 'a2ui_action' AND e.value ->> 'componentType' IS NOT NULL AND %1$s
                    GROUP BY 1
                )
                SELECT COALESCE(s.type, u.type) AS type, COALESCE(s.impressions, 0) AS impressions,
                       COALESCE(u.actions, 0) AS actions
                FROM shown s FULL OUTER JOIN used u ON u.type = s.type
                """.formatted(ACTOR);
        return jdbc.query(sql, params(userId, anonId, since),
                (rs, row) -> new ComponentUse(rs.getString("type"), rs.getLong("impressions"), rs.getLong("actions")));
    }

    /** Từ khóa tìm kiếm hay dùng (ô tìm kiếm thường), tối đa 8. */
    public List<Count> searches(UUID userId, String anonId, Instant since) {
        String sql = """
                SELECT lower(trim(e.value ->> 'q')) AS key, COUNT(*) AS count
                FROM public.ui_interaction_events e
                WHERE e.event_type = 'search' AND coalesce(trim(e.value ->> 'q'), '') <> '' AND %s
                GROUP BY 1 ORDER BY count DESC, max(e.occurred_at) DESC LIMIT 8
                """.formatted(ACTOR);
        return jdbc.query(sql, params(userId, anonId, since), (rs, row) -> new Count(rs.getString("key"),
                rs.getLong("count")));
    }

    /** Số lần mở trang do AI dựng (/ai), dùng để biết khách có quen dùng chế độ AI không. */
    public long aiPageViews(UUID userId, String anonId, Instant since) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.ui_interaction_events e
                WHERE e.event_type = 'page_view' AND e.route = '/ai' AND
                """ + ACTOR, params(userId, anonId, since), Long.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource params(UUID userId, String anonId, Instant since) {
        return new MapSqlParameterSource()
                .addValue("userId", userId == null ? null : userId.toString())
                .addValue("anonId", anonId)
                .addValue("since", Timestamp.from(since));
    }
}
