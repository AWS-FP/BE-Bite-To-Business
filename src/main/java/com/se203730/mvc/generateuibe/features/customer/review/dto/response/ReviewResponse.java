package com.se203730.mvc.generateuibe.features.customer.review.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewResponse(
        UUID id, UUID productId, UUID customerId, String reviewerName,
        BigDecimal rating, String content, Instant createdAt, Instant updatedAt,
        List<ReviewImageResponse> images
) {
    public ReviewResponse withImages(List<ReviewImageResponse> newImages) {
        return new ReviewResponse(id, productId, customerId, reviewerName,
                rating, content, createdAt, updatedAt, newImages);
    }
}
