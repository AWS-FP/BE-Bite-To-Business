package com.se203730.mvc.generateuibe.features.customer.entity;

import jakarta.persistence.Column;
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
@Table(name = "quotes", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@DynamicUpdate
public class Quote {
    @Id
    private UUID id;
    private String quoteNumber;
    private UUID customerId;
    private UUID couponId;
    private UUID shippingCouponId;
    private String status;
    private int revision;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String companyName;
    private String note;
    private UUID reviewedBy;
    private Integer reviewedRevision;
    private Instant reviewedAt;
    private String reviewNote;
    private Instant submittedAt;
    private Instant expiresAt;
    private BigDecimal itemsSubtotal;
    private BigDecimal volumeDiscountAmount;
    private BigDecimal saleDiscountAmount;
    private BigDecimal couponDiscountAmount;
    private BigDecimal shippingCouponDiscountAmount;
    private BigDecimal vatAmount;
    private BigDecimal shippingFee;
    @Column(name = "total_amount", insertable = false, updatable = false)
    private BigDecimal totalAmount;
    private Instant createdAt;
    private Instant updatedAt;
}
