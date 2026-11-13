package com.se203730.mvc.generateuibe.features.customer.entity;

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
@Table(name = "quote_package_items", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class QuotePackageItem {
    @Id
    private UUID id;
    private UUID quoteId;
    private UUID quotePackageId;
    private UUID quoteItemId;
    private BigDecimal quantity;
    private Instant createdAt;
    private Instant updatedAt;
}
