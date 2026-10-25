package com.se203730.mvc.generateuibe.features.auth.dto.request;

import jakarta.validation.constraints.*;

/**
 * Thông tin đăng nhập Gmail và mật khẩu; kiểm tra định dạng đầu vào, còn việc kiểm tra mật khẩu do Supabase thực hiện.
 */
public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        @Pattern(regexp = "(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@gmail\\.com")
        String email,
        @NotBlank
        @Size(max = 256)
        String password
) {
    public LoginRequest { email = RegisterRequest.normalizeEmail(email); }
    @Override public String toString() {
        return "LoginRequest[redacted]";
    }
}
