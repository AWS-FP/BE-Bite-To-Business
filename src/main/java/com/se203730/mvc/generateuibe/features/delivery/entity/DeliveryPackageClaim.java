package com.se203730.mvc.generateuibe.features.delivery.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_package_claims", schema = "public")
@Getter
@Setter
public class DeliveryPackageClaim {
    @Id
    private UUID packageId;
    private UUID deliveryStaffId;
    private Instant claimedAt;
}
