package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.dto.request.QuotePackageRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuotePackageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.math.RoundingMode;

@Service
public class QuotePackageRoutingService {
    @Autowired
    private QuotePackageService packages;
    @Autowired
    private DeliveryEstimateService delivery;

    public QuotePackageResponse save(UUID customerId, UUID quoteId, UUID packageId, QuotePackageRequest request) {
        packages.checkAddress(customerId, quoteId, packageId, request);
        DeliveryEstimateService.Estimate estimate = delivery.estimateCoordinates(
                request.latitude().setScale(6, RoundingMode.HALF_UP).doubleValue(),
                request.longitude().setScale(6, RoundingMode.HALF_UP).doubleValue(),
                request.city().equals("HCM") ? "hcm" : "hanoi", request.address());
        return packages.save(customerId, quoteId, packageId, request, estimate);
    }
}
