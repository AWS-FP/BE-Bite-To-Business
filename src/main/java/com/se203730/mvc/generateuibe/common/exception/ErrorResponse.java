package com.se203730.mvc.generateuibe.common.exception;

import java.time.Instant;
import java.util.List;

/**
 * Khung JSON phản hồi lỗi; errors mô tả từng trường sai và timestamp ghi thời điểm backend xử lý lỗi.
 */
public record ErrorResponse(
        int status,
        String code,
        String message,
        String path,
        List<ValidationError> errors,
        Instant timestamp
) {
    public ErrorResponse {
        errors = List.copyOf(errors);
    }

    // Không đưa giá trị đã nhập (password, OTP...) vào response lỗi.
    public record ValidationError(String field, String code, String message) {
    }
}
