package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuoteRevisionRequest(
        @NotNull
        @Min(1)
        Integer revision
) {}
