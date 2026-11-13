package com.se203730.mvc.generateuibe.features.delivery.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimedPackageResponse(UUID packageId, String packageNumber, String packageName,
                                     UUID orderId, String orderNumber, LocalDate deliveryDate,
                                     String deliveryTimeSlot, String address, String recipientName,
                                     String recipientPhone, String note, String city,
                                     BigDecimal latitude, BigDecimal longitude, Instant claimedAt,
                                     boolean cancelled) {
}
