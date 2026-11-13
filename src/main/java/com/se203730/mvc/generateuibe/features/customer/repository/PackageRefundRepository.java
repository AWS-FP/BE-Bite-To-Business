package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.PackageRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PackageRefundRepository extends JpaRepository<PackageRefund, UUID> {
    boolean existsByOrderPackageIdInAndStatusIn(Collection<UUID> packageIds, Collection<String> statuses);

    List<PackageRefund> findByOrderIdAndRequestedByOrderByCreatedAtDesc(UUID orderId, UUID customerId);
}
