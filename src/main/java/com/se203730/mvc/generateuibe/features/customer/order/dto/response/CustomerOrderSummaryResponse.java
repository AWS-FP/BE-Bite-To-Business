package com.se203730.mvc.generateuibe.features.customer.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerOrderSummaryResponse(
        UUID id,
        String orderNumber,
        String status,
        String paymentStatus,
        BigDecimal itemsSubtotal,
        BigDecimal vatAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        Instant confirmedAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant createdAt) {
}
