package com.se203730.mvc.generateuibe.features.customer.favorite.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Product card in the signed-in customer's Favorites screen. */
public record FavoriteProductResponse(
        UUID productId, String sku, String nameVi, String nameEn,
        String imageUrl, BigDecimal listPrice, Instant favoritedAt
) {}
