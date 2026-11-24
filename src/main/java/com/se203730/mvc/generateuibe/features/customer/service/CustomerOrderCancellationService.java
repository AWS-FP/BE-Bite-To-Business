package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.outbox.service.OutboxEventService;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.enums.OrderStatus;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Lets a customer cancel their own order while it is still unpaid. Paid orders go through the
 * refund flow instead. The order row lock serialises this with the SePay webhook, so a payment
 * that arrives after the cancel is kept for review rather than marking a cancelled order paid.
 */
@Service
@RequiredArgsConstructor
public class CustomerOrderCancellationService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final ReservationExpiryService reservations;
    private final DatabaseClock clock;
    private final OutboxEventService outbox;

    @Transactional
    public void cancel(UUID customerId, UUID orderId) {
        Order order = orders.findForUpdate(orderId)
                .filter(found -> found.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (payments.existsByOrderIdAndStatus(orderId, "SUCCESS")) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
        }
        Instant now = clock.now();
        if (order.getReservationExpiresAt() != null && order.getReservationReleasedAt() == null) {
            reservations.releaseReservedStock(order, now, "Customer cancelled before payment");
        }
        payments.cancelPending(orderId, now);
        if (orders.cancelByCustomer(orderId, customerId, now) != 1) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
        }
        outbox.record("order.cancelled", "ORDER", orderId, Map.of(
                "customerId", customerId,
                "cancelledAt", now,
                "reason", "CUSTOMER_CANCELLED_BEFORE_PAYMENT"));
    }
}
