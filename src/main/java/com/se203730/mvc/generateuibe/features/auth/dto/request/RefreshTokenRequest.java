package com.se203730.mvc.generateuibe.features.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Refresh token dùng để xin phiên đăng nhập mới khi access token hết hạn; không chứa thông tin role do client tự khai.
 */
public record RefreshTokenRequest(@NotBlank @Size(max = 4096) String refreshToken) {
    @Override public String toString() { return "RefreshTokenRequest[redacted]"; }
}
