package com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HomeProductResponse(
        UUID productId,
        String sku,
        String nameVi,
        String nameEn,
        String imageUrl,
        String unitCode,
        BigDecimal listPrice,
        BigDecimal salePrice,
        BigDecimal availableQuantity,
        UUID promotionId,
        String promotionNameVi,
        Instant promotionEndsAt
) {}
