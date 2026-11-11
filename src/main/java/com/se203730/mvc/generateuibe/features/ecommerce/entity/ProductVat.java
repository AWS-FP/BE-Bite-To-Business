package com.se203730.mvc.generateuibe.features.ecommerce.entity;

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
@Table(name = "products", schema = "public")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ProductVat {
    @Id
    private UUID id;
    private String sku;
    private BigDecimal vatRate;
    private Instant updatedAt;
}
