package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @Modifying
    @Query(value = "update public.payments set status = 'EXPIRED', updated_at = :now where order_id = :orderId and status = 'PENDING'", nativeQuery = true)
    int expirePending(@Param("orderId") UUID orderId, @Param("now") Instant now);
    Optional<Payment> findByOrderIdAndStatus(UUID orderId, String status);

    @Query("select p.orderId from Payment p where p.paymentNumber = :number")
    Optional<UUID> findOrderIdByPaymentNumber(
            @Param("number")
            String number);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentNumber = :number")
    Optional<Payment> findByPaymentNumberForUpdate(
            @Param("number")
            String number);

    boolean existsByOrderIdAndStatus(UUID orderId, String status);
}
