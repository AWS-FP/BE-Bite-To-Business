package com.se203730.mvc.generateuibe.features.admin.registration.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Quyết định APPROVE hoặc REJECT của Admin; nếu từ chối, service yêu cầu note ghi rõ lý do.
 */
public record ReviewRegistrationRequest(
        @NotNull Decision decision,
        @Size(max = 2000) String note
) {
    public enum Decision {
        APPROVE,
        REJECT
    }

    public ReviewRegistrationRequest {
        // Chuẩn hóa ghi chú; service kiểm tra lý do bắt buộc nếu quyết định là REJECT.
        if (note == null || note.isBlank()) {
            note = null;
        } else {
            note = note.strip();
        }
    }
}
