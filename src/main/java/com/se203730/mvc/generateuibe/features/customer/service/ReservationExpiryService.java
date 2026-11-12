package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.entity.InventoryMovement;
import com.se203730.mvc.generateuibe.features.customer.enums.OrderStatus;
import com.se203730.mvc.generateuibe.features.customer.repository.InventoryMovementRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.PaymentRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationExpiryService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final ProductInventoryRepository inventory;
    private final InventoryMovementRepository movements;
    private final DatabaseClock clock;

    @Transactional
    public void expire(UUID orderId) {
        var order = orders.findForUpdate(orderId).orElse(null);
        var now = clock.now();
        if (order == null || order.getStatus() != OrderStatus.CONFIRMED
                || order.getReservationReleasedAt() != null || order.getReservationExpiresAt() == null
                || now.isBefore(order.getReservationExpiresAt())
                || payments.existsByOrderIdAndStatus(orderId, "SUCCESS")) {
            return;
        }
        var balances = movements.findBalances(orderId);
        if (balances.isEmpty()) {
            throw new IllegalStateException("Reservation has no inventory movements: " + orderId);
        }
        for (var balance : balances) {
            if (balance.getShipped().signum() != 0 || balance.getReleased().compareTo(balance.getReserved()) > 0) {
                throw new IllegalStateException("Inconsistent inventory movements: " + orderId);
            }
            BigDecimal quantity = balance.getReserved().subtract(balance.getReleased());
            if (quantity.signum() == 0) {
                continue;
            }
            if (inventory.release(balance.getProductId(), quantity, now) != 1) {
                throw new IllegalStateException("Insufficient reserved stock: " + orderId);
            }
            InventoryMovement movement = new InventoryMovement();
            movement.setId(UUID.randomUUID());
            movement.setProductId(balance.getProductId());
            movement.setOrderId(orderId);
            movement.setMovementType("RELEASE");
            movement.setQuantity(quantity);
            movement.setReason("Payment reservation expired");
            movement.setCreatedBy(order.getCustomerId());
            movement.setCreatedAt(now);
            movements.save(movement);
        }
        payments.expirePending(orderId, now);
        if (orders.expireReservation(orderId, now) != 1) {
            throw new IllegalStateException("Could not expire reservation: " + orderId);
        }
    }
}
