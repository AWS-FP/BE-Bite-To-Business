package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
public record HomePageComboItemResponse(UUID comboId, UUID productId, String sku,
                                        String nameVi, String nameEn, String unitCode, BigDecimal quantity, String imageUrl) {}
