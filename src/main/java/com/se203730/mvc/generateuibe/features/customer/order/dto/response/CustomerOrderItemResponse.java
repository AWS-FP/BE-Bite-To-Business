package com.se203730.mvc.generateuibe.features.customer.order.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerOrderItemResponse(
        UUID id,
        UUID productId,
        String sku,
        String nameVi,
        String nameEn,
        String unitCode,
        BigDecimal quantity,
        BigDecimal saleUnitPrice,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal lineTotal) {
}
