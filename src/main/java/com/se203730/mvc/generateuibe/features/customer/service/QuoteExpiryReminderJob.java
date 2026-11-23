package com.se203730.mvc.generateuibe.features.customer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QuoteExpiryReminderJob {
    private final JdbcTemplate jdbc;
    private final NotificationEventPublisher events;

    // One reminder per quote, within the 24 hours before expiry. ON CONFLICT ensures
    // repeated polling or simultaneous instances do not create duplicate notifications.
    @Scheduled(fixedDelayString="${app.notification.quote-expiry-poll-ms:900000}")
    @Transactional
    public void sendReminders() {
        var upcoming = jdbc.query("""
            SELECT q.id,q.customer_id,q.quote_number
            FROM public.quotes q
            WHERE q.status IN ('PENDING_APPROVAL','APPROVED')
              AND q.expires_at > CURRENT_TIMESTAMP
              AND q.expires_at <= CURRENT_TIMESTAMP + INTERVAL '24 hours'
              AND NOT EXISTS (
                SELECT 1 FROM public.notifications n
                WHERE n.recipient_id=q.customer_id AND n.event_kind='QUOTE_EXPIRY'
                  AND n.event_source_id=q.id)
            ORDER BY q.expires_at,q.id LIMIT 200
            """,(rs,i)->new Reminder(rs.getObject("id",UUID.class),
                    rs.getObject("customer_id",UUID.class),rs.getString("quote_number")));
        for (var r : upcoming) {
            events.publish(r.customerId(),"QUOTE_EXPIRY",r.id(),"QUOTE",
                "Báo giá sắp hết hạn", "Your quote is expiring soon",
                "Báo giá " + r.number() + " sẽ hết hạn trong vòng 24 giờ.",
                "Quote " + r.number() + " expires within 24 hours.", "/quotes");
        }
    }
    private record Reminder(UUID id, UUID customerId,String number) {}
}
