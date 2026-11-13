package com.se203730.mvc.generateuibe.features.delivery.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.entity.OrderPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReadyDeliveryRepository extends JpaRepository<Order, UUID> {
    @Query(value = """
            select o.* from public.orders o
            where o.status in ('CONFIRMED', 'PROCESSING')
              and exists (select 1 from public.payments pay where pay.order_id = o.id
                    and pay.status = 'SUCCESS' and pay.paid_at is not null
                    and pay.currency = 'VND' and pay.amount = o.total_amount)
              and exists (select 1 from public.order_packages p where p.order_id = o.id
                    and p.cancelled_at is null and not exists
                    (select 1 from public.delivery_stops s where s.order_package_id = p.id)
                    and not exists (select 1 from public.delivery_package_claims c where c.package_id = p.id))
            order by o.created_at, o.id
            """, countQuery = """
            select count(*) from public.orders o
            where o.status in ('CONFIRMED', 'PROCESSING')
              and exists (select 1 from public.payments pay where pay.order_id = o.id
                    and pay.status = 'SUCCESS' and pay.paid_at is not null
                    and pay.currency = 'VND' and pay.amount = o.total_amount)
              and exists (select 1 from public.order_packages p where p.order_id = o.id
                    and p.cancelled_at is null and not exists
                    (select 1 from public.delivery_stops s where s.order_package_id = p.id)
                    and not exists (select 1 from public.delivery_package_claims c where c.package_id = p.id))
            """, nativeQuery = true)
    Page<Order> findReadyOrders(Pageable pageable);

    @Query("""
            select p from OrderPackage p where p.orderId in :orderIds and p.cancelledAt is null
            and not exists (select s.id from DeliveryStop s where s.orderPackageId = p.id)
            and not exists (select c.packageId from DeliveryPackageClaim c where c.packageId = p.id)
            order by p.orderId, p.deliveryDate, p.id
            """)
    List<OrderPackage> findReadyPackages(@Param("orderIds") Collection<UUID> orderIds);
}
