package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreatedOrderResponse(UUID orderId, String orderNumber, String status,
                                   BigDecimal totalAmount, Instant reservationExpiresAt) {
}
