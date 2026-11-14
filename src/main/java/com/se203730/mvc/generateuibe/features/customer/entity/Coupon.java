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
@Table(name = "coupons", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class Coupon {
    @Id
    private UUID id;
    private String code;
    private String appliesTo;
    private String productScope;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private Instant startsAt;
    private Instant endsAt;
    private Integer usageLimit;
    private Integer usageLimitPerUser;
    private boolean isActive;
}
