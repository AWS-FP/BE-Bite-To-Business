package com.se203730.mvc.generateuibe.features.customer.quote.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record AddQuoteItemRequest(
        @NotNull UUID productId,
        @NotNull @Positive BigDecimal quantity,
        @Size(max = 2000) String customerNote
) {}
