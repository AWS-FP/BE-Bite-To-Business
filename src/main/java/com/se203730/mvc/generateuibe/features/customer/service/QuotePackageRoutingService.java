package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.dto.request.QuotePackageRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuotePackageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class QuotePackageRoutingService {
    @Autowired
    private QuoteDraftService drafts;
    @Autowired
    private QuotePackageService packages;
    @Autowired
    private DeliveryEstimateService delivery;

    public QuotePackageResponse save(UUID customerId, UUID quoteId, UUID packageId, QuotePackageRequest request) {
        drafts.get(customerId, quoteId);
        DeliveryEstimateService.Estimate estimate = delivery.estimateCoordinates(
                request.latitude().doubleValue(), request.longitude().doubleValue(),
                request.city().equals("HCM") ? "hcm" : "hanoi", request.address());
        return packages.save(customerId, quoteId, packageId, request, estimate);
    }
}
