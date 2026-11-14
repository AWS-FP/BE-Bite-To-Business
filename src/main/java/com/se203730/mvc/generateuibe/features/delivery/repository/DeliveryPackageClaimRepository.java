package com.se203730.mvc.generateuibe.features.delivery.repository;

import com.se203730.mvc.generateuibe.features.delivery.entity.DeliveryPackageClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryPackageClaimRepository extends JpaRepository<DeliveryPackageClaim, UUID> {
    Page<DeliveryPackageClaim> findByDeliveryStaffIdAndDeliveredAtIsNullOrderByClaimedAtDescPackageIdAsc(
            UUID staffId, Pageable pageable);

    Page<DeliveryPackageClaim> findByDeliveryStaffIdAndDeliveredAtIsNotNullOrderByDeliveredAtDescPackageIdAsc(
            UUID staffId, Pageable pageable);
}
