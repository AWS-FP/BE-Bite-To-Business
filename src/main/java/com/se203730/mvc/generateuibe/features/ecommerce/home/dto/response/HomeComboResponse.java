package com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record HomeComboResponse(
        UUID productId,
        String sku,
        String nameVi,
        String nameEn,
        String imageUrl,
        Integer servingCount,
        BigDecimal listPrice,
        BigDecimal salePrice,
        BigDecimal availableQuantity,
        List<HomeComboItemResponse> items
) {}
