package com.se203730.mvc.generateuibe.features.customer.order.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record CustomerOrderPackageResponse(
        UUID packageId, String packageNumber, String packageName,
        LocalDate deliveryDate, String deliveryAddress,
        String recipientName, String recipientPhone, String deliveryTimeSlot,
        boolean cancelled) {}
