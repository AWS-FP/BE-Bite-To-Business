package com.se203730.mvc.generateuibe.features.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Ánh xạ bảng password_reset_requests: lần yêu cầu quên mật khẩu gần nhất của một tài khoản.
 * Không lưu OTP hay token; Supabase giữ và kiểm tra mã.
 */
@Entity
@Table(name = "password_reset_requests", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetRequest {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "otp_generated_at", nullable = false)
    private Instant otpGeneratedAt;

    @Column(name = "otp_sent_at")
    private Instant otpSentAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
