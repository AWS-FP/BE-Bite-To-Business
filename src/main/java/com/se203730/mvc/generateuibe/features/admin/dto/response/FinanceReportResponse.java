package com.se203730.mvc.generateuibe.features.admin.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinanceReportResponse(LocalDate from, LocalDate to, String group,
        String currency, String timezone, Summary summary, List<Period> periods) {
    public record Summary(long orders, long completedOrders, long cancelledOrders,
            BigDecimal completedAmount, long paymentCount, BigDecimal collected,
            long refundCount, BigDecimal refunded, BigDecimal netCollected,
            long openReviewCount, BigDecimal openReviewAmount) {}
    public record Period(LocalDate date, BigDecimal collected, BigDecimal refunded,
            BigDecimal netCollected, BigDecimal completedAmount, long completedOrders) {}
    public record Transaction(String id, String kind, Instant time, UUID orderId,
            String orderNumber, UUID customerId, String status, BigDecimal amount,
            String currency, String reference) {}
    public record Customer(UUID customerId, String name, long orders, long completedOrders,
            BigDecimal collected, BigDecimal refunded, BigDecimal netCollected) {}
}

