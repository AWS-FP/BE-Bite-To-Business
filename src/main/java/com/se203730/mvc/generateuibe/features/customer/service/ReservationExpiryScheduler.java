package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryScheduler {
    private final OrderRepository orders;
    private final ReservationExpiryService expiry;

    @Scheduled(fixedDelayString = "${app.reservation.expiry-poll-ms:30000}")
    public void expireReservations() {
        for (var orderId : orders.findExpiredReservations()) {
            try {
                expiry.expire(orderId);
            } catch (RuntimeException exception) {
                log.error("Could not release reservation for order {}", orderId, exception);
            }
        }
    }
}
