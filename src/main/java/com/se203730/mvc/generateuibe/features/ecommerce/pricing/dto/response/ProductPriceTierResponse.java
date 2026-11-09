
package com.se203730.mvc.generateuibe.features.ecommerce.pricing.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.pricing.entity.ProductPriceTier;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductPriceTierResponse(
    UUID id,
    BigDecimal minQuantity,
    BigDecimal maxQuantity,
    BigDecimal unitPrice
) {
    public static ProductPriceTierResponse from(ProductPriceTier tier) {
        return new ProductPriceTierResponse(
            tier.getId(),
            tier.getMinQuantity(),
            tier.getMaxQuantity(),
            tier.getUnitPrice()
        );
    }
}
