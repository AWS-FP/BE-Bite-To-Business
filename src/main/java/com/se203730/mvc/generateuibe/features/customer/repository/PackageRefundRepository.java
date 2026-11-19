package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.PackageRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PackageRefundRepository extends JpaRepository<PackageRefund, UUID> {
    boolean existsByOrderPackageIdInAndStatusIn(Collection<UUID> packageIds, Collection<String> statuses);

    @org.springframework.data.jpa.repository.Query("select r from PackageRefund r, OrderPackage p where r.orderPackageId = p.id and p.orderId = :orderId and r.requestedBy = :customerId order by r.createdAt desc")
    List<PackageRefund> findForOrder(UUID orderId, UUID customerId);
}
