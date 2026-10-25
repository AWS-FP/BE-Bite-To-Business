package com.se203730.mvc.generateuibe.features.admin.dto.request;

import jakarta.validation.constraints.*;

/**
 * Gmail và mã OTP Customer nhập sau khi Admin duyệt. Mã đúng định dạng vẫn phải được Supabase kiểm tra.
 */
public record VerifyOtpRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        @Pattern(regexp = "(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@gmail\\.com")
        String email,
        @NotBlank
        @Pattern(regexp = "[0-9]{6,10}")
        String otp
) {
    public VerifyOtpRequest {
        email = RegisterRequest.normalizeEmail(email);
    }
    @Override
    public String toString() {
        return "VerifyOtpRequest[redacted]";
    }
}
