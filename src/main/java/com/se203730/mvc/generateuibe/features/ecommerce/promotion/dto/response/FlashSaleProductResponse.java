package com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A product with a currently valid FLASH_SALE promotion. */
public record FlashSaleProductResponse(
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
