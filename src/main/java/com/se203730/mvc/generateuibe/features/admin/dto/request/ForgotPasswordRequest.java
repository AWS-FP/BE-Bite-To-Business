package com.se203730.mvc.generateuibe.features.admin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Gmail của tài khoản cần đặt lại mật khẩu. Response không cho biết Gmail có tồn tại hay không.
 */
public record ForgotPasswordRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        @Pattern(regexp = "(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@gmail\\.com")
        String email
) {
    public ForgotPasswordRequest {
        email = RegisterRequest.normalizeEmail(email);
    }
}
