package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query(value = "select id from public.orders where quote_id = :quoteId and customer_id = :customerId", nativeQuery = true)
    Optional<UUID> findIdByQuoteAndCustomer(@Param("quoteId") UUID quoteId, @Param("customerId") UUID customerId);
    @Query(value = """
            select o.id from public.orders o
            where o.reservation_managed = true and o.status = 'CONFIRMED' and o.reservation_released_at is null
              and o.reservation_expires_at is not null and o.reservation_expires_at <= clock_timestamp()
              and not exists (select 1 from public.payments p where p.order_id = o.id and p.status = 'SUCCESS')
            order by o.reservation_expires_at, o.id limit 50
            """, nativeQuery = true)
    List<UUID> findExpiredReservations();

    @Modifying
    @Query(value = """
            update public.orders set status = 'CANCELLED', cancelled_at = :now,
                cancellation_reason = 'Payment reservation expired', reservation_released_at = :now, updated_at = :now
            where id = :orderId and reservation_managed = true and status = 'CONFIRMED' and reservation_released_at is null
            """, nativeQuery = true)
    int expireReservation(@Param("orderId") UUID orderId, @Param("now") Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findForUpdate(@Param("id")
    UUID id);

    @Query(value = """
            select exists (
                select 1 from public.payments p
                where p.order_id = :orderId and p.status = 'SUCCESS'
                    and p.paid_at is not null and p.currency = 'VND' and p.amount = :amount
            )
            """, nativeQuery = true)
    boolean hasSuccessfulPayment(@Param("orderId")
    UUID orderId,
            @Param("amount")
            BigDecimal amount);
}
