package com.se203730.mvc.generateuibe.features.admin.dto.response;

import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationDuplicateAttempt;

import java.time.Instant;
import java.util.UUID;

/** Một lần đăng ký trùng (đã tự động từ chối) hiển thị cho admin. */
public record RegistrationDuplicateResponse(
        UUID id,
        String email,
        String phone,
        String firstName,
        String lastName,
        String duplicateField,
        UUID existingUserId,
        String emailStatus,
        Instant emailSentAt,
        Instant createdAt
) {
    public static RegistrationDuplicateResponse from(RegistrationDuplicateAttempt attempt) {
        return new RegistrationDuplicateResponse(attempt.getId(), attempt.getEmail(), attempt.getPhone(),
                attempt.getFirstName(), attempt.getLastName(), attempt.getDuplicateField(),
                attempt.getExistingUserId(), attempt.getEmailStatus(), attempt.getEmailSentAt(),
                attempt.getCreatedAt());
    }
}
