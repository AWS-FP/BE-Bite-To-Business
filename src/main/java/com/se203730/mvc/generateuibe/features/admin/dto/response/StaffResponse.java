package com.se203730.mvc.generateuibe.features.admin.dto.response;

import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import java.time.Instant;
import java.util.UUID;

public record StaffResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        UserRole role,
        boolean emailVerified,
        boolean banned,
        Instant createdAt
) {
    public static StaffResponse from(User user) {
        return new StaffResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhone(), user.getRole(), user.isEmailVerified(),
                user.isBanned(), user.getCreatedAt());
    }
}
