package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Coupon a customer can pick for a quote. {@code applicable} is false when the coupon is live but this
 * quote does not meet it yet (minimum order, or no eligible product); {@code reason} says which.
 */
public record AvailableCouponResponse(
        UUID id,
        String code,
        String appliesTo,
        String productScope,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Instant endsAt,
        boolean applicable,
        String reason,
        BigDecimal estimatedDiscount
) {
}
