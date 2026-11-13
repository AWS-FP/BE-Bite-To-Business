package com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record HomeComboItemResponse(
        UUID comboId,
        UUID productId,
        String sku,
        String nameVi,
        String unitCode,
        BigDecimal quantity,
        String imageUrl
) {}
