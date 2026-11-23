package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record HomePageProductResponse(
 UUID productId, String sku, String nameVi, String nameEn, String imageUrl,
 String unitCode, BigDecimal listPrice, BigDecimal salePrice, BigDecimal availableQuantity,
 UUID promotionId, String promotionNameVi, Instant promotionEndsAt,
 UUID categoryId, String categoryNameVi, String categoryNameEn,
 String packageSpecVi, String packageSpecEn, BigDecimal minOrderQuantity,
 BigDecimal quantityStep, BigDecimal vatRate, BigDecimal averageRating, long reviewCount
) {}
