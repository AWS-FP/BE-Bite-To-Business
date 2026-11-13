package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.constraints.Pattern;

public record QuoteCouponsRequest(
        @Pattern(regexp = "(?i)[A-Z0-9]{8}") String productCode,
        @Pattern(regexp = "(?i)[A-Z0-9]{8}") String shippingCode
) {}
