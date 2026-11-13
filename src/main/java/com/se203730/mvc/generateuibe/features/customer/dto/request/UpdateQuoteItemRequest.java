package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateQuoteItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity
) {}
