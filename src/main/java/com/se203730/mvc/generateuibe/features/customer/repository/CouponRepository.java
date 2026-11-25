package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    Optional<Coupon> findByCode(String code);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        insert into public.customer_coupon_history (customer_id, coupon_id, code)
        values (:customerId, :couponId, :code)
        on conflict (customer_id, coupon_id, code) do update set last_used_at = now()
        """, nativeQuery = true)
    void remember(@Param("customerId") UUID customerId, @Param("couponId") UUID couponId, @Param("code") String code);

    @Query(value = """
        select distinct c.* from public.coupons c
        where c.is_active = true and (
            exists (select 1 from public.customer_coupon_history h
                    where h.customer_id = :customerId and h.coupon_id = c.id and h.code = c.code)
            or exists (select 1 from public.orders o where o.customer_id = :customerId
                       and ((o.coupon_id = c.id and o.coupon_code = c.code)
                         or (o.shipping_coupon_id = c.id and o.shipping_coupon_code = c.code)))
        )
        order by c.code
        """, nativeQuery = true)
    List<Coupon> previouslyEntered(@Param("customerId") UUID customerId);

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
