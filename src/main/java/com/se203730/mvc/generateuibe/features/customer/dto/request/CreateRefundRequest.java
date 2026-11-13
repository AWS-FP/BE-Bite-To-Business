package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateRefundRequest(
        boolean entireOrder,
        @Size(max = 200)
        List<@NotNull UUID> packageIds,
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
