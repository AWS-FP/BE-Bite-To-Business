package com.se203730.mvc.generateuibe.features.customer.order.dto.response;

import java.util.List;

public record CustomerOrdersPageResponse(
        List<CustomerOrderSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
