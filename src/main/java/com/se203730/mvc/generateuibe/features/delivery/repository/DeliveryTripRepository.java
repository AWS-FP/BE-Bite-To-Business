package com.se203730.mvc.generateuibe.features.delivery.repository;

import com.se203730.mvc.generateuibe.features.delivery.entity.DeliveryTrip;
import com.se203730.mvc.generateuibe.features.delivery.enums.DeliveryTripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryTripRepository
        extends
            JpaRepository<DeliveryTrip, UUID>,
            JpaSpecificationExecutor<DeliveryTrip> {
    Optional<DeliveryTrip> findByIdAndDeliveryStaffId(UUID id, UUID deliveryStaffId);

    Optional<DeliveryTrip> findByDeliveryStaffIdAndStatusIn(
            UUID deliveryStaffId,
            Collection<DeliveryTripStatus> statuses);

    @Query("select t.orderId from DeliveryTrip t where t.id = :id and t.deliveryStaffId = :staffId")
    Optional<UUID> findAssignedOrderId(@Param("id")
    UUID id, @Param("staffId")
    UUID staffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DeliveryTrip t where t.id = :id and t.deliveryStaffId = :staffId")
    Optional<DeliveryTrip> findAssignedForUpdate(@Param("id")
    UUID id,
            @Param("staffId")
            UUID staffId);
}
