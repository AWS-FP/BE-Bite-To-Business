package com.se203730.mvc.generateuibe.features.delivery.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DeliveryStopResponse(
        UUID id,
        UUID packageId,
        String packageNumber,
        String packageName,
        LocalDate deliveryDate,
        String deliveryAddress,
        int stopOrder,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal legDistanceKm,
        Instant estimatedArrivalAt,
        Instant deliveredAt,
        Instant cancelledAt,
        List<DeliveryItemResponse> items
) {
}
