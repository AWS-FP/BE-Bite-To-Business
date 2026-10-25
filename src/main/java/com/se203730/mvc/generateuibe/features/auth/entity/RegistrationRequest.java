package com.se203730.mvc.generateuibe.features.auth.entity;

import com.se203730.mvc.generateuibe.features.auth.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Ánh xạ bảng registration_requests để lưu yêu cầu đăng ký và quá trình duyệt/kích hoạt; đây là entity database, không phải DTO nhận JSON.
 */
@Entity
@Table(name = "registration_requests", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationRequest {
    @Id
    private UUID id;
    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_note")
    private String reviewNote;
    @Column(name = "otp_sent_at")
    private Instant otpSentAt;
    // Chỉ ghi sau khi tạo mật khẩu thành công; APPROVED đơn thuần chưa cho phép mua hàng.
    @Column(name = "activated_at")
    private Instant activatedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
