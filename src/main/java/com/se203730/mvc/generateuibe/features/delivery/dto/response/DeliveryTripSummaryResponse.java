package com.se203730.mvc.generateuibe.features.delivery.dto.response;

import com.se203730.mvc.generateuibe.features.delivery.entity.DeliveryTrip;
import com.se203730.mvc.generateuibe.features.delivery.enums.DeliveryTripStatus;
import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record DeliveryTripSummaryResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        DeliveryTripStatus status,
        OrderStatus orderStatus,
        Instant plannedDepartureAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant createdAt
) {
    public static DeliveryTripSummaryResponse from(DeliveryTrip trip, Order order) {
        return new DeliveryTripSummaryResponse(trip.getId(), order.getId(), order.getOrderNumber(),
                trip.getStatus(), order.getStatus(), trip.getPlannedDepartureAt(),
                trip.getStartedAt(), trip.getCompletedAt(), trip.getCancelledAt(),
                trip.getCreatedAt());
    }
}
