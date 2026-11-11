package com.se203730.mvc.generateuibe.features.ecommerce.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QuoteDecisionRequest(
        @NotNull
        @Min(1)
        Integer revision,
        @NotBlank
        @Pattern(regexp = "APPROVE|RETURN_FOR_CHANGES")
        String decision,
        @Size(max = 1000)
        String note
) {}
