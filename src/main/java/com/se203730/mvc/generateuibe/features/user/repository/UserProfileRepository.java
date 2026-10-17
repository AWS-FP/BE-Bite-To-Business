package com.se203730.mvc.generateuibe.features.user.repository;

import com.se203730.mvc.generateuibe.features.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Đọc/ghi bảng user_profiles; khóa UUID của hồ sơ chính là ID tài khoản tương ứng trong bảng users.
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
