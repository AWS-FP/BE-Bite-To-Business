package com.se203730.mvc.generateuibe.features.customer.notification.repository;

import com.se203730.mvc.generateuibe.common.api.PageResponse;
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
    public PageResponse<NotificationResponse> list(UUID customerId,int page,int size,boolean unreadOnly) {
        String condition=unreadOnly ? " AND read_at IS NULL" : "";
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM public.notifications WHERE recipient_id=?"+condition,
                Long.class,customerId);
        List<NotificationResponse> items=jdbc.query("""
                SELECT id,notification_type,title_vi,title_en,content_vi,content_en,
                       action_url,read_at,created_at
                FROM public.notifications WHERE recipient_id=?
                """+condition+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
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
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM public.notifications WHERE recipient_id=? AND read_at IS NULL",
                Long.class,customerId);
        return count==null?0:count;
    }
    public boolean exists(UUID customerId,UUID id) {
        Boolean exists=jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM public.notifications WHERE recipient_id=? AND id=?)",
                Boolean.class,customerId,id);
        return Boolean.TRUE.equals(exists);
    }

    public int markRead(UUID customerId, UUID id) {
        return jdbc.update("""
            UPDATE public.notifications
            SET read_at = CURRENT_TIMESTAMP
            WHERE recipient_id = ?
              AND id = ?
              AND read_at IS NULL
            """, customerId, id);
    }

    public int markAllRead(UUID customerId) {
        return jdbc.update("""
            UPDATE public.notifications
            SET read_at = CURRENT_TIMESTAMP
            WHERE recipient_id = ?
              AND read_at IS NULL
            """, customerId);
    }

    private static java.time.Instant instant(Timestamp v) { return v==null?null:v.toInstant(); }
}
