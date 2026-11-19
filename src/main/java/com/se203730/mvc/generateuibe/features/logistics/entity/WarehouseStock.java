package com.se203730.mvc.generateuibe.features.logistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory", schema = "public")
@Getter
@Setter
public class WarehouseStock {
    @Id
    @Column(name = "product_id")
    private UUID productId;
    private BigDecimal onHandQuantity;
    private BigDecimal reservedQuantity;
    @Column(name = "available_quantity", insertable = false, updatable = false)
    private BigDecimal availableQuantity;
    private UUID updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
