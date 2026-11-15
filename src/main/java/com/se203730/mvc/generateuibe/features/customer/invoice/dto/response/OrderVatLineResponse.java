package com.se203730.mvc.generateuibe.features.customer.invoice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Values are read from the finalized order snapshot, not from mutable product prices. */
public record OrderVatLineResponse(
        UUID productId,
        String sku,
        String nameVi,
        BigDecimal quantity,
        BigDecimal vatRate,
        BigDecimal amountExcludingVat,
        BigDecimal vatAmount,
        BigDecimal lineTotal
) {}
