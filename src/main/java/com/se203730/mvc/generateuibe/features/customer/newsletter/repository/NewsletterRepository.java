package com.se203730.mvc.generateuibe.features.customer.newsletter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NewsletterRepository {
    private final JdbcTemplate jdbc;
    public boolean isReady() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass('public.newsletter_subscriptions') IS NOT NULL", Boolean.class));
    }
    /** Idempotent by normalized email; never sends or reveals other users' data. */
    public boolean subscribe(String email) {
        return !jdbc.query("""
                INSERT INTO public.newsletter_subscriptions (email,is_active,consented_at)
                VALUES (?,TRUE,CURRENT_TIMESTAMP)
                ON CONFLICT (email) DO UPDATE SET
                    is_active=TRUE, consented_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE newsletter_subscriptions.is_active = FALSE
                RETURNING id
                """, (rs, row) -> rs.getObject("id", java.util.UUID.class), email).isEmpty();
    }

    public record PromotionDelivery(java.util.UUID deliveryId, String email, String title, String content,
                                    String imageUrl) {}

    /** Queue only newly published, currently active campaigns, and only for subscribers who opted in earlier. */
    public int queueNewCampaigns() {
        return jdbc.update("""
                INSERT INTO public.newsletter_promotion_deliveries(subscription_id,promotion_id)
                SELECT s.id, p.id FROM public.newsletter_subscriptions s
                CROSS JOIN public.promotions p
                WHERE s.is_active = TRUE AND p.status = 'PUBLISHED'
                  AND p.published_at IS NOT NULL AND p.published_at >= s.consented_at
                  AND p.starts_at <= CURRENT_TIMESTAMP AND p.ends_at > CURRENT_TIMESTAMP
                ON CONFLICT (subscription_id,promotion_id) DO NOTHING
                """);
    }

    /** Lock and claim one queued delivery; another app instance cannot take the same row. */
    public PromotionDelivery claimOne() {
        var rows = jdbc.query("""
                WITH picked AS (
                    SELECT d.id FROM public.newsletter_promotion_deliveries d
                    JOIN public.newsletter_subscriptions s ON s.id=d.subscription_id
                    JOIN public.promotions p ON p.id=d.promotion_id
                    WHERE s.is_active = TRUE AND p.status='PUBLISHED'
                      AND p.starts_at<=CURRENT_TIMESTAMP AND p.ends_at>CURRENT_TIMESTAMP
                      AND d.attempts < 3 AND d.next_attempt_at <= CURRENT_TIMESTAMP
                      AND (d.status='PENDING' OR
                           (d.status='SENDING' AND d.claimed_at < CURRENT_TIMESTAMP - INTERVAL '15 minutes'))
                    ORDER BY d.next_attempt_at, d.created_at, d.id
                    FOR UPDATE OF d SKIP LOCKED LIMIT 1
                ), claimed AS (
                    UPDATE public.newsletter_promotion_deliveries d
                    SET status='SENDING', attempts=d.attempts+1, claimed_at=CURRENT_TIMESTAMP
                    FROM picked WHERE d.id=picked.id
                    RETURNING d.id, d.subscription_id, d.promotion_id
                )
                SELECT c.id,s.email,p.name_vi,p.content_vi,p.image_url
                FROM claimed c
                JOIN public.newsletter_subscriptions s ON s.id=c.subscription_id
                JOIN public.promotions p ON p.id=c.promotion_id
                """, (rs, row) -> new PromotionDelivery(
                rs.getObject("id", java.util.UUID.class), rs.getString("email"),
                rs.getString("name_vi"), rs.getString("content_vi"), rs.getString("image_url")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void markSent(java.util.UUID id) {
        jdbc.update("""
                UPDATE public.newsletter_promotion_deliveries
                SET status='SENT', sent_at=CURRENT_TIMESTAMP WHERE id=? AND status='SENDING'
                """, id);
    }

    public void retryLater(java.util.UUID id) {
        jdbc.update("""
                UPDATE public.newsletter_promotion_deliveries
                SET status='PENDING', next_attempt_at=CURRENT_TIMESTAMP + INTERVAL '1 hour'
                WHERE id=? AND status='SENDING'
                """, id);
    }
}
