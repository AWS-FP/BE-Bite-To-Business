package com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Active promotion and validated sale price for a particular product. */
public record ActiveSaleInfo(
        UUID productId,
        BigDecimal salePrice,
        UUID promotionId,
        String promotionNameVi,
        Instant promotionEndsAt
) {}
