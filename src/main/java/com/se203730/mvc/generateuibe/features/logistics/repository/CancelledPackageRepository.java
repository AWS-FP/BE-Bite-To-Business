package com.se203730.mvc.generateuibe.features.logistics.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.OrderPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;

public interface CancelledPackageRepository extends JpaRepository<OrderPackage, UUID> {
    interface PackageView {
        UUID getPackageId();
        UUID getOrderId();
        String getPackageName();
        String getPackageNumber();
        Instant getCancelledAt();
        Instant getProcessedAt();
    }

    interface ItemView {
        UUID getProductId();
        String getSku();
        String getName();
        String getUnit();
        BigDecimal getQuantity();
    }

    @Query(value = """
            select p.id as "packageId", p.order_id as "orderId", p.package_name as "packageName",
                   p.package_number as "packageNumber", p.cancelled_at as "cancelledAt", p.warehouse_processed_at as "processedAt"
            from public.order_packages p
            where p.cancelled_at is not null order by p.cancelled_at desc, p.id
            """, countQuery = "select count(*) from public.order_packages where cancelled_at is not null",
            nativeQuery = true)
    Page<PackageView> findCancelled(Pageable pageable);

    @Query(value = """
            select i.product_id as "productId", i.sku as sku, i.name_vi as name,
                   i.unit_code as unit, pi.quantity as quantity
            from public.package_items pi join public.order_items i on i.id = pi.order_item_id
            where pi.order_package_id = :packageId order by i.product_id
            """, nativeQuery = true)
    List<ItemView> findItems(@org.springframework.data.repository.query.Param("packageId") UUID packageId);

    @Query(value = "select exists(select 1 from public.order_packages where id = :packageId and warehouse_processed_at is not null)", nativeQuery = true)
    boolean isProcessed(@org.springframework.data.repository.query.Param("packageId") UUID packageId);

    @Query(value = "select exists(select 1 from public.inventory_movements where order_id = :orderId and (movement_type = 'SHIP' or (movement_type = 'RELEASE' and order_package_id is null)))", nativeQuery = true)
    boolean requiresManualReview(@org.springframework.data.repository.query.Param("orderId") UUID orderId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "update public.order_packages set warehouse_processed_by = :userId, warehouse_processed_at = :now, warehouse_processing_note = :note where id = :packageId and cancelled_at is not null and warehouse_processed_at is null", nativeQuery = true)
    int markProcessed(@org.springframework.data.repository.query.Param("packageId") UUID packageId,
                      @org.springframework.data.repository.query.Param("userId") UUID userId,
                      @org.springframework.data.repository.query.Param("now") Instant now,
                      @org.springframework.data.repository.query.Param("note") String note);
}
