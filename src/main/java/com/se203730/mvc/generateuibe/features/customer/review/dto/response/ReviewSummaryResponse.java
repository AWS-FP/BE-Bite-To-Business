package com.se203730.mvc.generateuibe.features.customer.review.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Rating summary of one product for catalog cards; averageRating is null when there are no reviews. */
public record ReviewSummaryResponse(
        UUID productId,
        long reviewCount,
        BigDecimal averageRating
) {
}
