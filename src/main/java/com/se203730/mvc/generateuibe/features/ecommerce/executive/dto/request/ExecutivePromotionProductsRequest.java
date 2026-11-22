package com.se203730.mvc.generateuibe.features.ecommerce.executive.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ExecutivePromotionProductsRequest(
    @NotNull List<@NotNull UUID> productIds,
    @Valid List<@Valid PriceUpdate> priceUpdates,
    List<@NotNull UUID> expectedProductIds
) {
    public record PriceUpdate(
        @NotNull UUID productId,
        @NotNull BigDecimal expectedListPrice,
        BigDecimal expectedSalePrice,
        BigDecimal expectedSaleDiscountPercent,
        BigDecimal newSalePrice
    ) {}
}
