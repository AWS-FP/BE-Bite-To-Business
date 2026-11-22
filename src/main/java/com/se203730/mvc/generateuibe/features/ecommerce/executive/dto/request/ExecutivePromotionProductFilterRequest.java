package com.se203730.mvc.generateuibe.features.ecommerce.executive.dto.request;

import java.util.UUID;

/** Null values mean no filter. */
public record ExecutivePromotionProductFilterRequest(
    String q,
    UUID brandId,
    UUID categoryId,
    boolean inStockOnly,
    boolean discountOnly
) {}
