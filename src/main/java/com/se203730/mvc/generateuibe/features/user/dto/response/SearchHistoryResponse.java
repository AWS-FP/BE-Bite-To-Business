package com.se203730.mvc.generateuibe.features.user.dto.response;

import com.se203730.mvc.generateuibe.features.user.entity.UserSearchHistory;

import java.time.Instant;
import java.util.UUID;

/** Không cần trả userId vì API chỉ trả lịch sử của tài khoản đang đăng nhập. */
public record SearchHistoryResponse(
        UUID id,
        String keyword,
        Instant searchedAt
) {
    public static SearchHistoryResponse from(UserSearchHistory history) {
        return new SearchHistoryResponse(
                history.getId(),
                history.getKeyword(),
                history.getSearchedAt());
    }
}
