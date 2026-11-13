package com.se203730.mvc.generateuibe.features.delivery.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReadyDeliveryOrderResponse(UUID orderId, String orderNumber, String customerName, List<Package> packages) {
    public record Package(UUID id, String number, String name, LocalDate deliveryDate, String address) {
    }
}
