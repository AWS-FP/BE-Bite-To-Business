package com.se203730.mvc.generateuibe.features.delivery.repository;

import com.se203730.mvc.generateuibe.features.delivery.entity.DeliveryStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryStopRepository extends JpaRepository<DeliveryStop, UUID> {
    List<DeliveryStop> findByTripIdOrderByStopOrderAsc(UUID tripId);
    boolean existsByOrderPackageId(UUID packageId);
}
