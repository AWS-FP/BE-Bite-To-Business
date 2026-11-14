package com.se203730.mvc.generateuibe.features.customer.favorite.dto.response;

import java.util.UUID;

public record FavoriteStatusResponse(UUID productId, boolean favorite) {}
