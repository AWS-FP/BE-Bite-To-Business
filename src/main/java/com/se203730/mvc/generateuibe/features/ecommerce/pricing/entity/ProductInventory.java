
package com.se203730.mvc.generateuibe.features.ecommerce.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory", schema = "public")
@Getter
@NoArgsConstructor
public class ProductInventory {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    // PostgreSQL tự tính:
    // on_hand_quantity - reserved_quantity
    @Column(
        name = "available_quantity",
        insertable = false,
        updatable = false,
        precision = 12,
        scale = 3
    )
    private BigDecimal availableQuantity;
}
