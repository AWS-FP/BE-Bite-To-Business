package com.se203730.mvc.generateuibe.features.user.entity;

import com.se203730.mvc.generateuibe.features.user.enums.UserRole;
import com.se203730.mvc.generateuibe.common.util.PhoneNumberUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Ánh xạ bảng users dùng chung cho Admin, Customer và ba role Staff; mật khẩu được Supabase Auth quản lý riêng.
 */
@Entity
@Table(name = "users", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class User {
    // UUID trùng với tài khoản auth.users trên Supabase; backend không tự sinh UUID khác cho cùng người dùng.
    @Id
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 15)
    private String phone;

    // Lưu tên role như CUSTOMER, ADMIN... để dữ liệu không phụ thuộc thứ tự enum trong Java.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    // Chỉ thành true khi quá trình xác minh email thành công; đây không phải cờ Admin đã duyệt hồ sơ.
    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "is_banned", nullable = false)
    private boolean banned;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Các luồng tạo/sửa tài khoản qua JPA đều lưu số điện thoại cùng một định dạng.
    @PrePersist
    @PreUpdate
    private void normalizePhone() {
        phone = PhoneNumberUtils.normalize(phone);
    }
}
