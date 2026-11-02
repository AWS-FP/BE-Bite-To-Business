package com.se203730.mvc.generateuibe.features.delivery.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DeliveryTripResponse(
        DeliveryTripSummaryResponse trip,
        String recipientName,
        String recipientPhone,
        String deliveryNote,
        Origin origin,
        String routePolyline,
        int speedKmPerHour,
        BigDecimal totalDistanceKm,
        Long estimatedDurationSeconds,
        Instant estimatedCompletionAt,
        int activePackages,
        int deliveredPackages,
        List<DeliveryStopResponse> stops
) {
    public record Origin(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
