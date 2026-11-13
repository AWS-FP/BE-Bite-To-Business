package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BankPaymentResponse(
        UUID id,
        UUID orderId,
        String paymentCode,
        BigDecimal amount,
        String currency,
        String status,
        String bank,
        String virtualAccount,
        String qrUrl
) {
}
