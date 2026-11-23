package com.se203730.mvc.generateuibe.features.ecommerce.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record ExecutivePricingRequest(
    @NotNull @DecimalMin("0") BigDecimal listPrice,
    @DecimalMin("0") BigDecimal salePrice,
    @DecimalMin(value="0",inclusive=false) @DecimalMax("100") BigDecimal saleDiscountPercent,
    @NotNull List<@NotNull @Valid Tier> tiers
) {
    public record Tier(UUID id, @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal minQuantity,
                       @DecimalMin(value="0",inclusive=false) BigDecimal maxQuantity,
                       @NotNull @DecimalMin("0") BigDecimal unitPrice) {}
}
