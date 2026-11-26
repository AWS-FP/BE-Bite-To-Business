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
        where (
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

    interface Usage {
        UUID getCouponId();
        long getTotal();
        long getUsed();
        long getReserved();
    }

    // The same rules serve both display and enforcement, including unexpired reservations.
    @Query(value = """
        select u.id as "couponId", count(*) as total,
               count(*) filter (where u.customer_id=:customerId and u.redeemed is not null) as used,
               count(*) filter (where u.customer_id=:customerId and u.redeemed is null) as reserved
        from (
            select coupon_id as id, 'PRODUCT' as scope, customer_id, coupon_redeemed_at as redeemed,
                   status, reservation_expires_at, reservation_released_at
            from public.orders where coupon_id in (:ids)
            union all
            select shipping_coupon_id, 'SHIPPING', customer_id, shipping_coupon_redeemed_at,
                   status, reservation_expires_at, reservation_released_at
            from public.orders where shipping_coupon_id in (:ids)
        ) u join public.coupons c on c.id=u.id and c.applies_to=u.scope
        where u.redeemed is not null or (u.status in ('CONFIRMED','PROCESSING')
              and (u.reservation_expires_at is null or u.reservation_expires_at > clock_timestamp())
              and u.reservation_released_at is null)
        group by u.id
        """, nativeQuery = true)
    List<Usage> usage(@Param("ids") List<UUID> ids, @Param("customerId") UUID customerId);

    interface ScopedProduct {
        UUID getCouponId();
        UUID getProductId();
        String getNameVi();
        String getNameEn();
    }

    @Query(value = """
        select cp.coupon_id as "couponId", p.id as "productId", p.name_vi as "nameVi", p.name_en as "nameEn"
        from public.coupon_products cp join public.products p on p.id=cp.product_id
        where cp.coupon_id in (:ids) and p.status='PUBLISHED' order by p.name_vi,p.id
        """, nativeQuery = true)
    List<ScopedProduct> scopedProducts(@Param("ids") List<UUID> ids);
}
