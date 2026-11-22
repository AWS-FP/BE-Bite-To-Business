package com.se203730.mvc.generateuibe.features.admin.repository;

import com.se203730.mvc.generateuibe.features.admin.entity.PasswordResetRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Truy cập bảng password_reset_requests. Service khóa dòng users trước khi đọc/ghi để hai request
 * quên mật khẩu của cùng tài khoản không chạy song song.
 */
public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, UUID> {
}
