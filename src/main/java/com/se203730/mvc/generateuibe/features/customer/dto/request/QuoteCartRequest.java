package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record QuoteCartRequest(@NotEmpty List<@NotNull @Valid UpdateQuoteItemRequest> items) {}
