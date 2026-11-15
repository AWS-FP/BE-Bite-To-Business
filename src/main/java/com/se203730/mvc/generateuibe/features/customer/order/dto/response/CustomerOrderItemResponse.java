package com.se203730.mvc.generateuibe.features.customer.order.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Snapshot from order_items, not current product prices or VAT. */
public record CustomerOrderItemResponse(
        UUID itemId, UUID productId, String sku, String nameVi, String nameEn,
        String unitCode, BigDecimal quantity, BigDecimal saleUnitPrice,
        BigDecimal vatRate, BigDecimal vatAmount, BigDecimal lineTotal) {}
