package com.se203730.mvc.generateuibe.features.customer.quote.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record QuoteAllocationRequest(@NotNull @Positive BigDecimal quantity) {}
