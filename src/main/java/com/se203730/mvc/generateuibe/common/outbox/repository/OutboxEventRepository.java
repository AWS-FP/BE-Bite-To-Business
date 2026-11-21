package com.se203730.mvc.generateuibe.common.outbox.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepository {
    private final JdbcTemplate jdbc;

    /** Ghi event; trùng idempotency_key thì bỏ qua và trả về false. */
    public boolean append(String eventType, String aggregateType, UUID aggregateId, String payloadJson,
            String idempotencyKey, String destination) {
        int inserted = jdbc.update("""
                INSERT INTO public.outbox_events
                    (event_type, aggregate_type, aggregate_id, payload, idempotency_key, destination)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, eventType, aggregateType, aggregateId, payloadJson, idempotencyKey, destination);
        return inserted == 1;
    }

    /** Nhận một lô event sẵn sàng; dòng PUBLISHING bị treo quá hạn được nhận lại. */
    public List<Event> claimBatch(UUID claimId, int limit, int staleClaimSeconds) {
        return jdbc.query("""
                UPDATE public.outbox_events
                SET status = 'PUBLISHING', claim_id = ?, claimed_at = now(), attempts = attempts + 1
                WHERE id IN (
                    SELECT id FROM public.outbox_events
                    WHERE (status = 'PENDING' AND next_attempt_at <= now())
                       OR (status = 'PUBLISHING' AND claimed_at < now() - make_interval(secs => ?))
                    ORDER BY next_attempt_at, created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                RETURNING id, event_type, event_version, aggregate_type, aggregate_id, payload::text AS payload,
                    destination, attempts, occurred_at
                """, (row, index) -> new Event(
                row.getObject("id", UUID.class),
                row.getString("event_type"),
                row.getInt("event_version"),
                row.getString("aggregate_type"),
                row.getObject("aggregate_id", UUID.class),
                row.getString("payload"),
                row.getString("destination"),
                row.getInt("attempts"),
                toInstant(row.getTimestamp("occurred_at"))), claimId, staleClaimSeconds, limit);
    }

    public int markPublished(UUID id, UUID claimId, String brokerMessageId) {
        return jdbc.update("""
                UPDATE public.outbox_events
                SET status = 'PUBLISHED', published_at = now(), broker_message_id = ?, last_error = NULL,
                    claim_id = NULL, claimed_at = NULL
                WHERE id = ? AND status = 'PUBLISHING' AND claim_id = ?
                """, brokerMessageId, id, claimId);
    }

    /** Hẹn thử lại; hết số lần thử thì chuyển FAILED để người vận hành xem lại. */
    public int retryLater(UUID id, UUID claimId, long delaySeconds, String error) {
        return jdbc.update("""
                UPDATE public.outbox_events
                SET status = CASE WHEN attempts >= max_attempts THEN 'FAILED' ELSE 'PENDING' END,
                    next_attempt_at = now() + make_interval(secs => ?),
                    last_error = LEFT(?, 500), claim_id = NULL, claimed_at = NULL
                WHERE id = ? AND status = 'PUBLISHING' AND claim_id = ?
                """, delaySeconds, error, id, claimId);
    }

    public int deletePublishedBefore(int retentionDays) {
        return jdbc.update("""
                DELETE FROM public.outbox_events
                WHERE status = 'PUBLISHED' AND published_at < now() - make_interval(days => ?)
                """, retentionDays);
    }

    public int deleteProcessedBefore(int retentionDays) {
        return jdbc.update("""
                DELETE FROM public.processed_events
                WHERE processed_at < now() - make_interval(days => ?)
                """, retentionDays);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record Event(UUID id, String eventType, int eventVersion, String aggregateType, UUID aggregateId,
            String payloadJson, String destination, int attempts, Instant occurredAt) {
    }
}
