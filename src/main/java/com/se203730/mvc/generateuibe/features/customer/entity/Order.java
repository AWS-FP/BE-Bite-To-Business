package com.se203730.mvc.generateuibe.features.customer.entity;

import com.se203730.mvc.generateuibe.features.customer.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "orders", schema = "public")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Order {
    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false, length = 50, updatable = false)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "coupon_id", updatable = false)
    private UUID couponId;

    @Column(name = "coupon_redeemed_at")
    private Instant couponRedeemedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "contact_name", nullable = false, length = 200, updatable = false)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 15, updatable = false)
    private String contactPhone;

    @Column(columnDefinition = "text", updatable = false)
    private String note;

    @Column(name = "total_amount", insertable = false, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at", updatable = false)
    private Instant cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
