package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class QuoteAddressEmailRepository {
    @Autowired
    private JdbcTemplate jdbc;

    public void enqueue(UUID quoteId, UUID packageId, int revision, String email, String quoteNumber, String reason) {
        jdbc.update("""
                INSERT INTO public.quote_address_email_outbox
                    (id, quote_id, quote_package_id, quote_revision, recipient_email, quote_number, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), quoteId, packageId, revision, email, quoteNumber, reason);
    }

    public Email claim(UUID claimId) {
        List<Email> claimed = jdbc.query("""
                UPDATE public.quote_address_email_outbox
                SET status = 'SENDING', claim_id = ?, claimed_at = now(), attempts = attempts + 1
                WHERE id = (
                    SELECT id FROM public.quote_address_email_outbox
                    WHERE (status = 'PENDING' AND next_attempt_at <= now())
                       OR (status = 'SENDING' AND claimed_at < now() - interval '5 minutes')
                    ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1
                )
                RETURNING id, recipient_email, quote_number, reason
                """, (row, index) -> new Email(row.getObject("id", UUID.class),
                row.getString("recipient_email"), row.getString("quote_number"), row.getString("reason")), claimId);
        return claimed.isEmpty() ? null : claimed.get(0);
    }

    public void markSent(UUID id, UUID claimId) {
        jdbc.update("""
                UPDATE public.quote_address_email_outbox
                SET status = 'SENT', sent_at = now(), claim_id = NULL, claimed_at = NULL
                WHERE id = ? AND status = 'SENDING' AND claim_id = ?
                """, id, claimId);
    }

    public void retryLater(UUID id, UUID claimId) {
        jdbc.update("""
                UPDATE public.quote_address_email_outbox
                SET status = 'PENDING', next_attempt_at = now() + interval '5 minutes',
                    claim_id = NULL, claimed_at = NULL
                WHERE id = ? AND status = 'SENDING' AND claim_id = ?
                """, id, claimId);
    }

    public record Email(UUID id, String recipient, String quoteNumber, String reason) {}
}
