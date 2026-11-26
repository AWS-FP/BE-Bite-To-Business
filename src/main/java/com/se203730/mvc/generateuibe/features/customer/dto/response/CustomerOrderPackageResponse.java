package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerOrderPackageResponse(
        UUID packageId, String packageNumber, String packageName,
        LocalDate deliveryDate, String deliveryAddress,
        String recipientName, String recipientPhone,
        boolean cancelled, String deliveryStatus, Instant deliveredAt,
        String returnStatus, boolean returnable, BigDecimal packageAmount,
        BigDecimal returnFee, BigDecimal refundAmount) {}
