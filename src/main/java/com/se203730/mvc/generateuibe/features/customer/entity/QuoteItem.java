package com.se203730.mvc.generateuibe.features.customer.entity;

import jakarta.persistence.Column;
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
@Table(name = "quote_items", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class QuoteItem {
    @Id
    private UUID id;
    private UUID quoteId;
    private UUID productId;
    private UUID priceTierId;
    private String sku;
    private String nameVi;
    private String nameEn;
    private String unitCode;
    private BigDecimal quantity;
    private BigDecimal listUnitPrice;
    private BigDecimal volumeUnitPrice;
    private BigDecimal saleUnitPrice;
    private BigDecimal couponDiscountAmount;
    private BigDecimal vatRate;
    private boolean vatRateConfigured;
    private String customerNote;
    @Column(name = "line_total", insertable = false, updatable = false)
    private BigDecimal lineTotal;
    private Instant createdAt;
    private Instant updatedAt;
}
