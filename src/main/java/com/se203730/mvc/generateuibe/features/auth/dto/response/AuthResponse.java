package com.se203730.mvc.generateuibe.features.auth.dto.response;

import com.se203730.mvc.generateuibe.features.user.dto.response.UserResponse;

/**
 * Phiên đăng nhập trả cho frontend gồm access token, refresh token, thời hạn và thông tin người dùng.
 */
public record AuthResponse(String accessToken, String refreshToken, String tokenType,
                           long expiresIn, UserResponse user) {
    @Override public String toString() { return "AuthResponse[redacted]"; }
}
