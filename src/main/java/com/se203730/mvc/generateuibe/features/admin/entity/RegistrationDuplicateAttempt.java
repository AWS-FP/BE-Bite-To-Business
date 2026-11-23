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
 * Đăng ký trùng email/số điện thoại với tài khoản đã có. API công khai trả biên nhận như bình thường
 * để không lộ email/số nào đã đăng ký; bản ghi này để admin xem và tự động gửi mail từ chối.
 */
@Entity
@Table(name = "registration_duplicate_attempts", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationDuplicateAttempt {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    /** EMAIL, PHONE hoặc EMAIL_AND_PHONE. */
    @Column(name = "duplicate_field", nullable = false)
    private String duplicateField;

    @Column(name = "existing_user_id")
    private UUID existingUserId;

    /** PENDING, SENT, SKIPPED hoặc FAILED. */
    @Column(name = "email_status", nullable = false)
    private String emailStatus;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts;

    @Column(name = "next_email_at", nullable = false)
    private Instant nextEmailAt;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
