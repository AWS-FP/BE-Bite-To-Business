package com.se203730.mvc.generateuibe.features.customer.order.dto.response;

import java.util.List;

public record CustomerOrderDetailResponse(
        CustomerOrderSummaryResponse order,
        List<CustomerOrderItemResponse> items,
        List<CustomerOrderPackageResponse> packages) {}
