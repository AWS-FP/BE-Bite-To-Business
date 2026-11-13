package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QuoteContactRequest(
        @NotBlank
        @Size(max = 200)
        String name,
        @NotBlank
        @Pattern(regexp = "(?:\\+[0-9]{8,14}|[0-9]{8,15})")
        String phone,
        @NotBlank
        @Email
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        @Size(max = 254)
        String email,
        @Size(max = 200)
        String companyName,
        @Size(max = 1000)
        String note
) {
}
