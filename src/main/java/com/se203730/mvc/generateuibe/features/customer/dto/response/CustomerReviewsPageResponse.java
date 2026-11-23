package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.util.List;

public record CustomerReviewsPageResponse(
        List<ReviewResponse> content, int page, int size, long totalElements, int totalPages) {}
