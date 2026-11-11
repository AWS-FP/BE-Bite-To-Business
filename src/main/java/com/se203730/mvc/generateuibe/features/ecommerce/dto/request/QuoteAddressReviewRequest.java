package com.se203730.mvc.generateuibe.features.ecommerce.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuoteAddressReviewRequest(
        @NotNull
        @Min(1)
        Integer revision,
        @NotNull
        Boolean valid,
        @Size(max = 1000)
        String reason
) {}
