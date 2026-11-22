package com.se203730.mvc.generateuibe.features.customer.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationEventPublisher {
    private final JdbcTemplate jdbc;

    public void publish(UUID recipientId,String kind,UUID sourceId,String notificationType,
                        String titleVi,String titleEn,String contentVi,String contentEn,String actionUrl) {
        // Values are produced by trusted server-side workflows, not a public send API.
        jdbc.update("""
            INSERT INTO public.notifications
              (recipient_id,notification_type,title_vi,title_en,content_vi,content_en,
               action_url,event_kind,event_source_id)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT DO NOTHING
            """,recipientId,notificationType,titleVi,titleEn,contentVi,contentEn,
                actionUrl,kind,sourceId);
    }
}
