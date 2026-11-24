package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePackageReturnRequest(
        @NotBlank
        @Size(max = 1000)
        String reason,
        @NotBlank
        @Size(max = 100)
        String bank,
        @NotBlank
        @Pattern(regexp = "[0-9]{1,34}")
        String accountNumber,
        @NotBlank
        @Size(max = 200)
        String accountName
) {
}
