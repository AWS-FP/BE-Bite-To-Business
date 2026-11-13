package com.se203730.mvc.generateuibe.features.customer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements", schema = "public")
@Getter
@Setter
public class InventoryMovement {
    @Id
    private UUID id;
    private UUID productId;
    private UUID orderId;
    private String movementType;
    private BigDecimal quantity;
    private String reason;
    private UUID createdBy;
    private Instant createdAt;
}
