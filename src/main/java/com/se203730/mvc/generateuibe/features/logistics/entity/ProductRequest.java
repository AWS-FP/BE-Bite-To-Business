package com.se203730.mvc.generateuibe.features.logistics.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_requests", schema = "public")
@Getter
@Setter
public class ProductRequest {
    @Id
    private UUID id;
    private UUID customerId;
    private String productName;
    private String referenceUrl;
    private String contactEmail;
    private boolean notifyByEmail;
    private String status;
    private String customerNote;
    private Instant createdAt;
    private Instant updatedAt;
}
