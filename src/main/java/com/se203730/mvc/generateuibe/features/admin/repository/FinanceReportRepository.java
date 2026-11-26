package com.se203730.mvc.generateuibe.features.admin.repository;

import com.se203730.mvc.generateuibe.features.admin.dto.response.FinanceReportResponse.*;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceReportRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public FinanceReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Aggregate independent events before joining so retries and split-package refunds cannot multiply income.
    private static final String ACTIVITY = """
            WITH activity AS (
              SELECT o.customer_id, o.created_at AS time, 'ORDER' AS kind, 0::numeric AS amount
                FROM public.orders o
              UNION ALL
              SELECT o.customer_id, o.completed_at, 'COMPLETED', o.total_amount
                FROM public.orders o WHERE o.status = 'COMPLETED' AND o.completed_at IS NOT NULL
              UNION ALL
              SELECT o.customer_id, o.cancelled_at, 'CANCELLED', 0::numeric
                FROM public.orders o WHERE o.status = 'CANCELLED' AND o.cancelled_at IS NOT NULL
              UNION ALL
              SELECT o.customer_id, p.paid_at, 'PAYMENT', p.amount
                FROM public.payments p JOIN public.orders o ON o.id = p.order_id
                WHERE p.status = 'SUCCESS' AND p.currency = 'VND'
              UNION ALL
              SELECT o.customer_id, r.refunded_at, 'REFUND', r.refund_amount
                FROM public.package_refunds r JOIN public.payments p ON p.id = r.payment_id
                JOIN public.orders o ON o.id = p.order_id
                WHERE r.status = 'REFUNDED' AND r.refunded_at IS NOT NULL AND p.currency = 'VND'
              UNION ALL
              SELECT o.customer_id, r.refunded_at, 'REFUND', r.refund_amount
                FROM public.package_returns r JOIN public.order_packages pkg ON pkg.id = r.order_package_id
                JOIN public.orders o ON o.id = pkg.order_id
                WHERE r.refunded_at IS NOT NULL AND r.refund_amount IS NOT NULL
            ), filtered AS (
              SELECT * FROM activity WHERE time >= :start AND time < :end
                AND (CAST(:customerId AS uuid) IS NULL OR customer_id = CAST(:customerId AS uuid))
            )
            """;
    private static final String AGGREGATES = """
            COUNT(*) FILTER (WHERE kind = 'ORDER') AS orders,
            COUNT(*) FILTER (WHERE kind = 'COMPLETED') AS completed,
            COUNT(*) FILTER (WHERE kind = 'CANCELLED') AS cancelled,
            COALESCE(SUM(amount) FILTER (WHERE kind = 'COMPLETED'), 0) AS completed_amount,
            COUNT(*) FILTER (WHERE kind = 'PAYMENT') AS payments,
            COALESCE(SUM(amount) FILTER (WHERE kind = 'PAYMENT'), 0) AS collected,
            COUNT(*) FILTER (WHERE kind = 'REFUND') AS refunds,
            COALESCE(SUM(amount) FILTER (WHERE kind = 'REFUND'), 0) AS refunded
            """;

    public Summary summary(MapSqlParameterSource params) {
        return jdbc.queryForObject(ACTIVITY + " SELECT " + AGGREGATES + """
                , (SELECT COUNT(*) FROM public.sepay_webhook_events e
                     LEFT JOIN public.payments p ON p.id = e.payment_id
                     LEFT JOIN public.orders o ON o.id = p.order_id
                     WHERE e.result = 'REVIEW' AND e.resolution IS NULL AND LOWER(e.transfer_type) = 'in'
                       AND e.received_at >= :start AND e.received_at < :end
                       AND (CAST(:customerId AS uuid) IS NULL OR o.customer_id = CAST(:customerId AS uuid))) AS reviews,
                  (SELECT COALESCE(SUM(e.amount), 0) FROM public.sepay_webhook_events e
                     LEFT JOIN public.payments p ON p.id = e.payment_id
                     LEFT JOIN public.orders o ON o.id = p.order_id
                     WHERE e.result = 'REVIEW' AND e.resolution IS NULL AND LOWER(e.transfer_type) = 'in'
                       AND e.received_at >= :start AND e.received_at < :end
                       AND (CAST(:customerId AS uuid) IS NULL OR o.customer_id = CAST(:customerId AS uuid))) AS review_amount
                  FROM filtered
                """, params, (rs, row) -> new Summary(rs.getLong("orders"), rs.getLong("completed"),
                rs.getLong("cancelled"), rs.getBigDecimal("completed_amount"), rs.getLong("payments"),
                rs.getBigDecimal("collected"), rs.getLong("refunds"), rs.getBigDecimal("refunded"),
                rs.getBigDecimal("collected").subtract(rs.getBigDecimal("refunded")),
                rs.getLong("reviews"), rs.getBigDecimal("review_amount")));
    }

    public List<Period> periods(MapSqlParameterSource params) {
        return jdbc.query(ACTIVITY + """
                SELECT CAST(time AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS date,
                """ + AGGREGATES + " FROM filtered GROUP BY 1 ORDER BY 1", params,
                (rs, row) -> new Period(rs.getDate("date").toLocalDate(), rs.getBigDecimal("collected"),
                        rs.getBigDecimal("refunded"), rs.getBigDecimal("collected").subtract(rs.getBigDecimal("refunded")),
                        rs.getBigDecimal("completed_amount"), rs.getLong("completed")));
    }

    public PageResponse<Customer> customers(MapSqlParameterSource params, int page, int size) {
        return page(ACTIVITY, """
                SELECT f.customer_id, concat_ws(' ', u.last_name, u.first_name) AS name,
                COUNT(*) FILTER (WHERE f.kind = 'ORDER') AS orders,
                COUNT(*) FILTER (WHERE f.kind = 'COMPLETED') AS completed,
                COALESCE(SUM(f.amount) FILTER (WHERE f.kind = 'PAYMENT'), 0) AS collected,
                COALESCE(SUM(f.amount) FILTER (WHERE f.kind = 'REFUND'), 0) AS refunded
                FROM filtered f JOIN public.users u ON u.id = f.customer_id
                GROUP BY f.customer_id, u.last_name, u.first_name
                ORDER BY collected DESC, f.customer_id LIMIT :limit OFFSET :offset
                """, "SELECT COUNT(DISTINCT customer_id) FROM filtered", params, page, size,
                (rs, row) -> new Customer(rs.getObject("customer_id", UUID.class), rs.getString("name"),
                        rs.getLong("orders"), rs.getLong("completed"), rs.getBigDecimal("collected"),
                        rs.getBigDecimal("refunded"), rs.getBigDecimal("collected").subtract(rs.getBigDecimal("refunded"))));
    }

    private static final String TRANSACTIONS = """
            WITH transactions AS (
              SELECT p.id::text AS id, 'PAYMENT' AS kind, COALESCE(p.paid_at, p.created_at) AS time,
                     o.id AS order_id, o.order_number, o.customer_id, p.status, p.amount, p.currency,
                     p.provider_transaction_id AS reference
                FROM public.payments p JOIN public.orders o ON o.id = p.order_id
              UNION ALL
              SELECT r.id::text, 'REFUND', COALESCE(r.refunded_at, r.created_at),
                     o.id, o.order_number, o.customer_id, r.status, r.refund_amount, p.currency, r.transfer_reference
                FROM public.package_refunds r JOIN public.payments p ON p.id = r.payment_id
                JOIN public.orders o ON o.id = p.order_id
              UNION ALL
              SELECT r.id::text, 'REFUND', COALESCE(r.refunded_at, r.created_at),
                     o.id, o.order_number, o.customer_id,
                     CASE WHEN r.refunded_at IS NOT NULL THEN 'REFUNDED'
                          WHEN r.status = 'ACCEPTED' THEN 'APPROVED' ELSE r.status END,
                     r.refund_amount, 'VND', r.transfer_reference
                FROM public.package_returns r JOIN public.order_packages pkg ON pkg.id = r.order_package_id
                JOIN public.orders o ON o.id = pkg.order_id
              UNION ALL
              SELECT e.transaction_id::text, 'SEPAY_REVIEW', e.received_at,
                     o.id, o.order_number, o.customer_id, COALESCE(e.resolution, e.result),
                     e.amount, 'VND', e.reference_code
                FROM public.sepay_webhook_events e LEFT JOIN public.payments p ON p.id = e.payment_id
                LEFT JOIN public.orders o ON o.id = p.order_id
                WHERE e.result = 'REVIEW' AND LOWER(e.transfer_type) = 'in'
            ), filtered AS (
              SELECT * FROM transactions WHERE time >= :start AND time < :end
                AND (CAST(:customerId AS uuid) IS NULL OR customer_id = CAST(:customerId AS uuid))
                AND (:kind = 'ALL' OR kind = :kind) AND (:status = 'ALL' OR status = :status)
            )
            """;

    public PageResponse<Transaction> transactions(MapSqlParameterSource params, int page, int size) {
        return page(TRANSACTIONS, """
                SELECT * FROM filtered ORDER BY time DESC, kind, id LIMIT :limit OFFSET :offset
                """, "SELECT COUNT(*) FROM filtered", params, page, size,
                (rs, row) -> new Transaction(rs.getString("id"), rs.getString("kind"),
                        rs.getTimestamp("time").toInstant(), rs.getObject("order_id", UUID.class),
                        rs.getString("order_number"), rs.getObject("customer_id", UUID.class),
                        rs.getString("status"), rs.getBigDecimal("amount"), rs.getString("currency"),
                        rs.getString("reference")));
    }

    private <T> PageResponse<T> page(String cte, String query, String countQuery,
            MapSqlParameterSource params, int page, int size, RowMapper<T> mapper) {
        params.addValue("limit", size).addValue("offset", (long) page * size);
        long count = jdbc.queryForObject(cte + countQuery, params, Long.class);
        List<T> rows = jdbc.query(cte + query, params, mapper);
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }
}

