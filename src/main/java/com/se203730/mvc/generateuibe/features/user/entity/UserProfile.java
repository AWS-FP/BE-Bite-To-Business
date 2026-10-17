package com.se203730.mvc.generateuibe.features.user.entity;

import com.se203730.mvc.generateuibe.features.user.enums.CustomerType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Hồ sơ dùng chung; thông tin không áp dụng cho role có thể để null. */
@Entity
@Table(name = "user_profiles", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {
    // user_id vừa là khóa chính hồ sơ vừa liên kết đến users.id; mỗi tài khoản có tối đa một hồ sơ.
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 50)
    private CustomerType customerType;
    @Column(name = "business_name", length = 200)
    private String businessName;
    @Column(length = 100)
    private String province;
    @Column(length = 100)
    private String district;
    @Column(length = 100)
    private String ward;
    @Column(name = "delivery_address")
    private String deliveryAddress;
    // Boolean có ba giá trị: true, false và null khi thông tin này không áp dụng cho role.
    @Column(name = "invoice_same_as_delivery")
    private Boolean invoiceSameAsDelivery;
    @Column(name = "invoice_purchaser", length = 200)
    private String invoicePurchaser;
    @Column(name = "invoice_tax_code", length = 10)
    private String invoiceTaxCode;
    @Column(name = "invoice_email", length = 254)
    private String invoiceEmail;
    @Column(name = "invoice_address")
    private String invoiceAddress;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
