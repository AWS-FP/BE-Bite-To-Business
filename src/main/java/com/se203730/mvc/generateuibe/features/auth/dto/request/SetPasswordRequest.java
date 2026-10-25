package com.se203730.mvc.generateuibe.features.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mật khẩu mới và giá trị nhập lại sau OTP; service kiểm tra hai giá trị trùng nhau trước khi gửi tới Supabase.
 */
public record SetPasswordRequest(
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 8, max = 72) String confirmPassword
) {
    @Override public String toString() { return "SetPasswordRequest[redacted]"; }
}
