
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
@Table(name = "product_prices", schema = "public")
@Getter
@NoArgsConstructor
public class ProductPrice {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "list_price", nullable = false, precision = 14, scale = 0)
    private BigDecimal listPrice;

    @Column(name = "sale_price", precision = 14, scale = 0)
    private BigDecimal salePrice;
}
