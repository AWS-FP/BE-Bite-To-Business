package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** History is owner-scoped and written only by the backend, never from an arbitrary client URL. */
@Service
public class AvatarHistoryService {
    private static final int HISTORY_LIMIT = 6;

    @Autowired
    private JdbcTemplate jdbc;

    public List<String> recent(UUID userId, String currentUrl) {
        return jdbc.queryForList("""
                SELECT avatar_url FROM public.user_avatar_history
                WHERE user_id = ? AND avatar_url <> COALESCE(?, '')
                ORDER BY last_used_at DESC, avatar_url
                LIMIT ?
                """, String.class, userId, currentUrl, HISTORY_LIMIT);
    }

    /** Called only for a successfully uploaded avatar or the user's already stored current avatar. */
    public void remember(UUID userId, String url) {
        if (url == null || !url.startsWith("https://")) return;
        jdbc.update("""
                INSERT INTO public.user_avatar_history (user_id, avatar_url, last_used_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (user_id, avatar_url)
                DO UPDATE SET last_used_at = EXCLUDED.last_used_at
                """, userId, url);
    }

    public void requireOwned(UUID userId, String url) {
        if (url == null || !url.startsWith("https://") || !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public.user_avatar_history WHERE user_id = ? AND avatar_url = ?
                )
                """, Boolean.class, userId, url))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
