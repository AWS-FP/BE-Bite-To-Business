package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    Optional<Coupon> findByCode(String code);

    @Query(value = "select product_id from public.coupon_products where coupon_id = :couponId", nativeQuery = true)
    List<UUID> findProductIds(@Param("couponId") UUID couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> findForUpdate(@Param("id") UUID id);

    @Query(value = """
        select count(*) from public.orders o
        where ((:scope = 'PRODUCT' and o.coupon_id = :couponId and o.coupon_redeemed_at is not null)
            or (:scope = 'SHIPPING' and o.shipping_coupon_id = :couponId and o.shipping_coupon_redeemed_at is not null)
            or (o.status in ('CONFIRMED', 'PROCESSING')
                and (o.reservation_expires_at is null or o.reservation_expires_at > clock_timestamp())
                and o.reservation_released_at is null
                and ((:scope = 'PRODUCT' and o.coupon_id = :couponId)
                  or (:scope = 'SHIPPING' and o.shipping_coupon_id = :couponId))))
          and (cast(:customerId as uuid) is null or o.customer_id = :customerId)
        """, nativeQuery = true)
    long countUsedOrReserved(@Param("couponId") UUID couponId, @Param("scope") String scope,
                             @Param("customerId") UUID customerId);
}
