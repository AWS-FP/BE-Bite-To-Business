
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
@Table(name = "product_price_tiers", schema = "public")
@Getter
@NoArgsConstructor
public class ProductPriceTier {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "min_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal minQuantity;

    @Column(name = "max_quantity", precision = 12, scale = 3)
    private BigDecimal maxQuantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 0)
    private BigDecimal unitPrice;
}
