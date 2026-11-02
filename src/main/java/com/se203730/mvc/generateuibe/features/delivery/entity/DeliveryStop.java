package com.se203730.mvc.generateuibe.features.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "delivery_stops", schema = "public")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class DeliveryStop {
    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "order_package_id", nullable = false, unique = true, updatable = false)
    private UUID orderPackageId;

    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    @Column(name = "destination_latitude", precision = 9, scale = 6)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", precision = 9, scale = 6)
    private BigDecimal destinationLongitude;

    @Column(name = "leg_distance_km", precision = 12, scale = 3)
    private BigDecimal legDistanceKm;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
