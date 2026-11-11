package com.se203730.mvc.generateuibe.features.delivery.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_staff_availability", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryStaffAvailability {
    @Id
    private UUID userId;
    private String status;
    private Instant updatedAt;
}
