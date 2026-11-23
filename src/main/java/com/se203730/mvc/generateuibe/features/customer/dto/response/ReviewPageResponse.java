package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ReviewPageResponse(
        List<ReviewResponse> content, int page, int size,
        long totalElements, int totalPages, BigDecimal averageRating
) {}
