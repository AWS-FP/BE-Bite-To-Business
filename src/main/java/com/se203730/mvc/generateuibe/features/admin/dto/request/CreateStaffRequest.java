package com.se203730.mvc.generateuibe.features.admin.dto.request;

import com.se203730.mvc.generateuibe.common.util.PhoneNumberUtils;
import com.se203730.mvc.generateuibe.features.admin.enums.StaffRole;
import jakarta.validation.constraints.*;

public record   CreateStaffRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*")
        String firstName,
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*")
        String lastName,
        @NotBlank
        @Email
        @Size(max = 254)
        @Pattern(regexp = "(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@gmail\\.com")
        String email,
        @NotBlank
        @Pattern(regexp = "\\+84[35789][0-9]{8}")
        String phone,
        @NotNull
        StaffRole role,
        @NotBlank
        @Size(min = 8, max = 72)
        String password,
        @NotBlank
        @Size(min = 8, max = 72)
        String confirmPassword
) {
    public CreateStaffRequest {
        firstName = RegisterRequest.trim(firstName);
        lastName = RegisterRequest.trim(lastName);
        email = RegisterRequest.normalizeEmail(email);
        phone = PhoneNumberUtils.normalize(phone);
    }

    @Override
    public String toString() {
        return "CreateStaffRequest[redacted]";
    }
}
