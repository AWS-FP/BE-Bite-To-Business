package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuoteAvailabilityResponse(boolean available, List<Item> items) {
    public record Item(UUID productId, String sku, BigDecimal requestedQuantity,
                       BigDecimal availableQuantity, boolean available) {
    }
}
