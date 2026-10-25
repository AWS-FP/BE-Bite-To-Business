package com.se203730.mvc.generateuibe.features.admin.dto.response;

import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Biên nhận sau khi Guest gửi form: ID yêu cầu, trạng thái chờ duyệt và thời điểm gửi; chưa cấp phiên đăng nhập.
 */
public record RegistrationReceipt(
        UUID id,
        RegistrationStatus status,
        Instant submittedAt
) {
}
