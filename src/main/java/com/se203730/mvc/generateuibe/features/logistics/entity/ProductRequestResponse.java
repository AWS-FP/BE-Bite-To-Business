package com.se203730.mvc.generateuibe.features.logistics.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_request_responses", schema = "public")
@Getter
@Setter
public class ProductRequestResponse {
    @Id
    private UUID id;
    private UUID requestId;
    private UUID respondedBy;
    private String responseNote;
    private UUID addedProductId;
    private String productName;
    private Instant productAddedAt;
    private BigDecimal sellingPrice;
    private BigDecimal availableQuantity;
    private String unitCode;
    private String recipientEmail;
    private Instant notifiedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
