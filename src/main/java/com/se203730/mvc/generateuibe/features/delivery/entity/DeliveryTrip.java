package com.se203730.mvc.generateuibe.features.delivery.entity;

import com.se203730.mvc.generateuibe.features.delivery.enums.DeliveryTripStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_trips", schema = "public")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class DeliveryTrip {
    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "delivery_staff_id")
    private UUID deliveryStaffId;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryTripStatus status;

    @Column(name = "origin_latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal originLatitude;

    @Column(name = "origin_longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal originLongitude;

    @Column(name = "route_polyline", columnDefinition = "text")
    private String routePolyline;

    @Column(name = "planned_departure_at")
    private Instant plannedDepartureAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
