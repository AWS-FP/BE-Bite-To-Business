package com.se203730.mvc.generateuibe.features.customer.invoice.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** VAT snapshot for customer display; it is NOT a legally issued e-invoice. */
public record OrderVatPreviewResponse(
        UUID orderId,
        String orderNumber,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        List<OrderVatLineResponse> items,
        String disclaimer
) {}
