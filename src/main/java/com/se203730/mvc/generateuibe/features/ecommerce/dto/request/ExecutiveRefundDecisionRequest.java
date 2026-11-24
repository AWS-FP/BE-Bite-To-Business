package com.se203730.mvc.generateuibe.features.ecommerce.dto.request;

import jakarta.validation.constraints.Size;

public record ExecutiveRefundDecisionRequest(
    @Size(max = 2000)
    String note,
    @Size(max = 200)
    String transferReference
) {
}
