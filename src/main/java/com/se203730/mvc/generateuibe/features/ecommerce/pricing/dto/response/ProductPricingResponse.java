
package com.se203730.mvc.generateuibe.features.ecommerce.pricing.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductPricingResponse(
    UUID productId,
    String sku,
    String unitCode,
    String currency,
    BigDecimal minOrderQuantity,
    BigDecimal quantityStep,
    boolean priceAvailable,
    BigDecimal listPrice,
    BigDecimal salePrice,
    boolean saleActive,
    List<ProductPriceTierResponse> priceTiers,
    boolean inventoryAvailable,
    BigDecimal availableQuantity
) {
}
