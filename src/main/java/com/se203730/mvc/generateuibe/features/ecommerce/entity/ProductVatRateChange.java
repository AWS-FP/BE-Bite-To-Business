package com.se203730.mvc.generateuibe.features.ecommerce.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_vat_rate_changes", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ProductVatRateChange {
    @Id
    private UUID id;
    private UUID productId;
    private BigDecimal previousRate;
    private BigDecimal newRate;
    private UUID changedBy;
    private Instant changedAt;
}
