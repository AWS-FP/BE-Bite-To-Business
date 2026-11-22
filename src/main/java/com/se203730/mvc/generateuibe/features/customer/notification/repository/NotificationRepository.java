package com.se203730.mvc.generateuibe.features.customer.notification.repository;

import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.features.customer.notification.dto.request.NotificationPreferencesRequest;
import com.se203730.mvc.generateuibe.features.customer.notification.dto.response.NotificationPreferencesResponse;
import com.se203730.mvc.generateuibe.features.customer.notification.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {
    private final JdbcTemplate jdbc;

    // Generic legacy notifications are preserved. Only explicitly typed events are muted.
    private static final String VISIBLE = """
        FROM public.notifications n
        LEFT JOIN public.customer_notification_preferences pref ON pref.customer_id=n.recipient_id
        WHERE n.recipient_id=?
        AND (n.event_kind IS NULL
             OR (n.event_kind='QUOTE_APPROVAL' AND COALESCE(pref.quote_approval,TRUE))
             OR (n.event_kind='QUOTE_EXPIRY' AND COALESCE(pref.quote_expiry,TRUE))
             OR (n.event_kind='DELIVERY_ETA' AND COALESCE(pref.delivery_eta,TRUE))
             OR (n.event_kind='SOURCING_REPLY' AND COALESCE(pref.sourcing_reply,TRUE)))
        """;

    public PageResponse<NotificationResponse> list(UUID customerId,int page,int size,boolean unreadOnly) {
        String condition=unreadOnly ? " AND n.read_at IS NULL" : "";
        Long count=jdbc.queryForObject("SELECT COUNT(*) " + VISIBLE + condition, Long.class,customerId);
        List<NotificationResponse> items=jdbc.query("""
                SELECT n.id,n.notification_type,n.title_vi,n.title_en,n.content_vi,n.content_en,
                       n.action_url,n.read_at,n.created_at
                """ + VISIBLE + condition + " ORDER BY n.created_at DESC,n.id DESC LIMIT ? OFFSET ?",
            (rs,rowNum)->new NotificationResponse(
                rs.getObject("id",UUID.class),rs.getString("notification_type"),
                rs.getString("title_vi"),rs.getString("title_en"),
                rs.getString("content_vi"),rs.getString("content_en"),
                rs.getString("action_url"),rs.getTimestamp("read_at")!=null,
                instant(rs.getTimestamp("read_at")),instant(rs.getTimestamp("created_at"))),
            customerId,size,(long)page*size);
        long total=count==null?0:count;
        return new PageResponse<>(items,page,size,total,(int)((total+size-1)/size));
    }
    public long unreadCount(UUID customerId) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) " + VISIBLE + " AND n.read_at IS NULL",
            Long.class,customerId);
        return count==null?0:count;
    }
    public NotificationPreferencesResponse preferences(UUID customerId) {
        List<NotificationPreferencesResponse> records=jdbc.query("""
                SELECT quote_approval,quote_expiry,delivery_eta,sourcing_reply
                FROM public.customer_notification_preferences WHERE customer_id=?
                """,(rs,i)->new NotificationPreferencesResponse(
            rs.getBoolean("quote_approval"),rs.getBoolean("quote_expiry"),
            rs.getBoolean("delivery_eta"),rs.getBoolean("sourcing_reply")),customerId);
        return records.isEmpty()?NotificationPreferencesResponse.defaults():records.get(0);
    }
    public NotificationPreferencesResponse updatePreferences(UUID customerId, NotificationPreferencesRequest request) {
        jdbc.update("""
            INSERT INTO public.customer_notification_preferences
              (customer_id,quote_approval,quote_expiry,delivery_eta,sourcing_reply)
            VALUES (?,?,?,?,?)
            ON CONFLICT (customer_id) DO UPDATE SET
              quote_approval=EXCLUDED.quote_approval,
              quote_expiry=EXCLUDED.quote_expiry,
              delivery_eta=EXCLUDED.delivery_eta,
              sourcing_reply=EXCLUDED.sourcing_reply,
              updated_at=NOW()
            """,customerId,request.quoteApproval(),request.quoteExpiry(),
            request.deliveryEta(),request.sourcingReply());
        return preferences(customerId);
    }
    public boolean exists(UUID customerId,UUID id) {
        Boolean exists=jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM public.notifications WHERE recipient_id=? AND id=?)",
            Boolean.class,customerId,id);
        return Boolean.TRUE.equals(exists);
    }
    public int markRead(UUID customerId, UUID id) {
        return jdbc.update("""
            UPDATE public.notifications SET read_at=CURRENT_TIMESTAMP
            WHERE recipient_id=? AND id=? AND read_at IS NULL
            """,customerId,id);
    }
    public int markAllRead(UUID customerId) {
        // Mark only currently visible notifications; suppressed alerts remain unread until re-enabled.
        return jdbc.update("""
            UPDATE public.notifications n SET read_at=CURRENT_TIMESTAMP
            FROM public.users u
            LEFT JOIN public.customer_notification_preferences pref ON pref.customer_id=u.id
            WHERE n.recipient_id=u.id AND u.id=? AND n.read_at IS NULL
            AND (n.event_kind IS NULL
             OR (n.event_kind='QUOTE_APPROVAL' AND COALESCE(pref.quote_approval,TRUE))
             OR (n.event_kind='QUOTE_EXPIRY' AND COALESCE(pref.quote_expiry,TRUE))
             OR (n.event_kind='DELIVERY_ETA' AND COALESCE(pref.delivery_eta,TRUE))
             OR (n.event_kind='SOURCING_REPLY' AND COALESCE(pref.sourcing_reply,TRUE)))
            """,customerId);
    }
    private static java.time.Instant instant(Timestamp v) { return v==null?null:v.toInstant(); }
}
