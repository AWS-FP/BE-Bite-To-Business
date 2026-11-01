package com.se203730.mvc.generateuibe.features.admin.dto.response;

import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import java.util.UUID;

/**
 * Thông tin tài khoản được trả cho frontend; chuyển từ entity User và không chứa mật khẩu hoặc API key.
 */
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        UserRole role,
        boolean emailVerified,
        String avatarUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(),
                user.getPhone(), user.getRole(), user.isEmailVerified(), user.getAvatarUrl());
    }
}
