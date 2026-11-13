package com.se203730.mvc.generateuibe.features.ecommerce.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVatResponse(UUID productId, String sku, BigDecimal vatRate) {
}
