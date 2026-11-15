package com.se203730.mvc.generateuibe.features.customer.home.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record HomePageComboResponse(
 UUID productId, String sku, String nameVi, String nameEn, String imageUrl,
 Integer servingCount, BigDecimal listPrice, BigDecimal salePrice,
 BigDecimal availableQuantity, List<HomePageComboItemResponse> items,
 BigDecimal minOrderQuantity, BigDecimal quantityStep, BigDecimal vatRate,
 BigDecimal averageRating, long reviewCount
) {}
