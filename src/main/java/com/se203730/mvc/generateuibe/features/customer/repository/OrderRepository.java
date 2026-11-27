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
    interface ProductQuantity {
        UUID getProductId();

        BigDecimal getQuantity();
    }

    /** Tổng số lượng từng sản phẩm của đơn, để giữ hàng lại khi khôi phục đơn đã huỷ. */
    @Query(value = """
            select c.product_id as "productId", sum(oi.quantity * c.factor) as quantity
            from public.order_items oi join checkout_stock.order_components c on c.order_item_id=oi.id
            where oi.order_id = :orderId
            group by c.product_id order by c.product_id
            """, nativeQuery = true)
    List<ProductQuantity> findProductQuantities(@Param("orderId") UUID orderId);

    /**
     * Admin khôi phục đơn từ khoản tiền khách đã chuyển (đơn đã hết hạn giữ hàng hoặc đã bị huỷ).
     * Gỡ trạng thái huỷ, ghi đã thanh toán và ghi lượt dùng coupon giống luồng webhook.
     */
    @Modifying
    @Query(value = """
            update public.orders set status = COMPLETED, completed_at = :now,
                cancelled_at = null, cancelled_by = null, cancellation_reason = null,
                reservation_released_at = null,
                coupon_redeemed_at = case when coupon_id is not null and coupon_redeemed_at is null
                    then :now else coupon_redeemed_at end,
                shipping_coupon_redeemed_at = case when shipping_coupon_id is not null
                    and shipping_coupon_redeemed_at is null then :now else shipping_coupon_redeemed_at end,
                updated_at = :now
            where id = :orderId and status in (CONFIRMED, CANCELLED)
            """, nativeQuery = true)
    int restorePaid(@Param("orderId") UUID orderId, @Param("now") Instant now);

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

    // Khách tự hủy đơn chưa thanh toán; giữ hàng (nếu có) được đánh dấu đã trả cùng lúc.
    @Modifying
    @Query(value = """
            update public.orders set status = 'CANCELLED', cancelled_at = :now, cancelled_by = :customerId,
                cancellation_reason = 'Customer cancelled before payment',
                reservation_released_at = case
                    when reservation_started_at is not null and reservation_released_at is null then :now
                    else reservation_released_at end,
                updated_at = :now
            where id = :orderId and customer_id = :customerId and status = 'CONFIRMED'
            """, nativeQuery = true)
    int cancelByCustomer(@Param("orderId") UUID orderId, @Param("customerId") UUID customerId,
                         @Param("now") Instant now);
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
