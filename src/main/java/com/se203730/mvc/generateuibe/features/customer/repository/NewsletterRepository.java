package com.se203730.mvc.generateuibe.features.customer.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NewsletterRepository {
    private final JdbcTemplate jdbc;
    public record PromotionDelivery(java.util.UUID deliveryId, String email, String title, String content,
                                    String imageUrl) {}

    /** Queue only newly published, currently active campaigns, and only for subscribers who opted in earlier. */
    public int queueNewCampaigns() {
        return jdbc.update("""
                INSERT INTO public.promotion_email_deliveries(customer_id,promotion_id)
                SELECT s.id, p.id FROM public.users s
                CROSS JOIN public.promotions p
                WHERE s.role = 'CUSTOMER' AND s.newsletter_subscribed = TRUE AND s.is_email_verified = TRUE AND s.is_banned = FALSE AND p.status = 'PUBLISHED'
                  AND p.published_at IS NOT NULL AND p.published_at >= s.newsletter_consented_at
                  AND p.starts_at <= CURRENT_TIMESTAMP AND p.ends_at > CURRENT_TIMESTAMP
                ON CONFLICT (customer_id,promotion_id) DO NOTHING
                """);
    }

    /** Lock and claim one queued delivery; another app instance cannot take the same row. */
    public PromotionDelivery claimOne() {
        var rows = jdbc.query("""
                WITH picked AS (
                    SELECT d.id FROM public.promotion_email_deliveries d
                    JOIN public.users s ON s.id=d.customer_id
                    JOIN public.promotions p ON p.id=d.promotion_id
                    WHERE s.role='CUSTOMER' AND s.newsletter_subscribed = TRUE AND s.is_email_verified = TRUE AND s.is_banned = FALSE AND p.status='PUBLISHED'
                      AND p.starts_at<=CURRENT_TIMESTAMP AND p.ends_at>CURRENT_TIMESTAMP
                      AND d.attempts < 3 AND d.next_attempt_at <= CURRENT_TIMESTAMP
                      AND (d.status='PENDING' OR
                           (d.status='SENDING' AND d.claimed_at < CURRENT_TIMESTAMP - INTERVAL '15 minutes'))
                    ORDER BY d.next_attempt_at, d.created_at, d.id
                    FOR UPDATE OF d SKIP LOCKED LIMIT 1
                ), claimed AS (
                    UPDATE public.promotion_email_deliveries d
                    SET status='SENDING', attempts=d.attempts+1, claimed_at=CURRENT_TIMESTAMP
                    FROM picked WHERE d.id=picked.id
                    RETURNING d.id, d.customer_id, d.promotion_id
                )
                SELECT c.id,s.email,p.name_vi,p.content_vi,p.image_url
                FROM claimed c
                JOIN public.users s ON s.id=c.customer_id
                JOIN public.promotions p ON p.id=c.promotion_id
                """, (rs, row) -> new PromotionDelivery(
                rs.getObject("id", java.util.UUID.class), rs.getString("email"),
                rs.getString("name_vi"), rs.getString("content_vi"), rs.getString("image_url")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void markSent(java.util.UUID id) {
        jdbc.update("""
                UPDATE public.promotion_email_deliveries
                SET status='SENT', sent_at=CURRENT_TIMESTAMP WHERE id=? AND status='SENDING'
                """, id);
    }

    public void retryLater(java.util.UUID id) {
        jdbc.update("""
                UPDATE public.promotion_email_deliveries
                SET status='PENDING', next_attempt_at=CURRENT_TIMESTAMP + INTERVAL '1 hour'
                WHERE id=? AND status='SENDING'
                """, id);
    }
}
