package com.se203730.mvc.generateuibe.features.logistics.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.OrderPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.UUID;

public interface CancelledPackageRepository extends JpaRepository<OrderPackage, UUID> {
    interface PackageView {
        UUID getPackageId();
        UUID getOrderId();
        String getPackageName();
        String getPackageNumber();
        Instant getCancelledAt();
    }

    @Query(value = """
            select p.id as "packageId", p.order_id as "orderId", p.package_name as "packageName",
                   p.package_number as "packageNumber", p.cancelled_at as "cancelledAt"
            from public.order_packages p
            where p.cancelled_at is not null order by p.cancelled_at desc, p.id
            """, countQuery = "select count(*) from public.order_packages where cancelled_at is not null",
            nativeQuery = true)
    Page<PackageView> findCancelled(Pageable pageable);
}
