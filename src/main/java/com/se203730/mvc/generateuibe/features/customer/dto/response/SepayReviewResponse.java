package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SepayReviewResponse(
        Long transactionId,
        UUID paymentId,
        String paymentCode,
        BigDecimal amount,
        String referenceCode,
        String reviewReason,
        Instant receivedAt
) {
}
