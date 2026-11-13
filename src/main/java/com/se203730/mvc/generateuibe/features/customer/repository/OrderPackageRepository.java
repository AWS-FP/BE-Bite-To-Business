package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.OrderPackage;
import com.se203730.mvc.generateuibe.features.customer.repository.projection.PackageComboItemView;
import com.se203730.mvc.generateuibe.features.customer.repository.projection.PackageItemView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPackageRepository extends JpaRepository<OrderPackage, UUID> {
    @Query("select p.orderId from OrderPackage p where p.id = :id")
    Optional<UUID> findOrderIdByPackageId(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OrderPackage p where p.id = :id")
    Optional<OrderPackage> findForUpdate(@Param("id") UUID id);
    List<OrderPackage> findByOrderIdOrderByIdAsc(UUID orderId);

    @Query(value = """
            select pi.order_package_id as "packageId", oi.id as "orderItemId",
                oi.sku as "sku", oi.name_vi as "nameVi", oi.name_en as "nameEn",
                oi.unit_code as "unitCode", pi.quantity as "quantity",
                oi.customer_note as "customerNote"
            from public.package_items pi
            join public.order_items oi on oi.id = pi.order_item_id and oi.order_id = pi.order_id
            where pi.order_id = :orderId
            order by pi.order_package_id, oi.sku, oi.id
            """, nativeQuery = true)
    List<PackageItemView> findItems(@Param("orderId")
    UUID orderId);

    @Query(value = """
            select pi.order_package_id as "packageId", ci.order_item_id as "orderItemId",
                ci.sku as "sku", ci.name_vi as "nameVi", ci.name_en as "nameEn",
                ci.unit_code as "unitCode", pi.quantity * ci.quantity_per_combo as "quantity"
            from public.package_items pi
            join public.order_combo_items ci on ci.order_item_id = pi.order_item_id
            where pi.order_id = :orderId
            order by pi.order_package_id, ci.order_item_id, ci.sku, ci.product_id
            """, nativeQuery = true)
    List<PackageComboItemView> findComboItems(@Param("orderId")
    UUID orderId);
}
