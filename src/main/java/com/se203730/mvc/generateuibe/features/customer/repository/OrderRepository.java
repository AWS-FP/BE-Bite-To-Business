package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
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
