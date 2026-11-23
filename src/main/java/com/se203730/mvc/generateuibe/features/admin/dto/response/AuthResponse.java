package com.se203730.mvc.generateuibe.features.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.se203730.mvc.generateuibe.features.admin.dto.response.UserResponse;

/**
 * Phiên đăng nhập trả cho frontend gồm access token, refresh token, thời hạn và thông tin người dùng.
 */
public record AuthResponse(
        // null trong response: access token chỉ đi trong cookie HttpOnly btb_access.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String accessToken,
        // Controller bỏ trường này (null) trước khi trả về: refresh token chỉ đi trong cookie HttpOnly.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    @Override
    public String toString() {
        return "AuthResponse[redacted]";
    }
}
