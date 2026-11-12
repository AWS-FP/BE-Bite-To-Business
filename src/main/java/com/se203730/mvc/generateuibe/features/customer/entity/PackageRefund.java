package com.se203730.mvc.generateuibe.features.customer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "package_refunds", schema = "public")
@Getter
@Setter
public class PackageRefund {
    @Id
    private UUID id;
    private UUID orderId;
    private UUID orderPackageId;
    private UUID paymentId;
    private UUID requestedBy;
    private UUID requestGroupId;
    private String status;
    private String reason;
    private String recipientBank;
    private String recipientAccount;
    private String recipientName;
    private Instant createdAt;
    private Instant updatedAt;
}
