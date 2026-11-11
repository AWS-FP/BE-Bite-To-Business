package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.util.List;
import java.util.UUID;

public record QuoteReviewResponse(
        UUID customerId,
        String contactName,
        String contactPhone,
        String contactEmail,
        String companyName,
        QuoteDraftResponse quote,
        List<QuotePackageResponse> packages
) {}
