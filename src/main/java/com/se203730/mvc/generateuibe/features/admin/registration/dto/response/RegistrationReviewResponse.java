package com.se203730.mvc.generateuibe.features.admin.registration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kết quả duyệt hồ sơ cùng cờ otpSent; đã duyệt thành công vẫn có thể cần gửi lại OTP nếu dịch vụ email lỗi.
 */
@Schema(description = "Kết quả Admin duyệt hoặc từ chối đăng ký Customer")
public record RegistrationReviewResponse(
        RegistrationResponse registration,

        // false khi từ chối hoặc khi đã duyệt nhưng gửi OTP lỗi; xem registration.status để phân biệt.
        @Schema(description = "SMTP đã chấp nhận gửi OTP trong thao tác duyệt này; không chứa mã OTP", example = "true")
        boolean otpSent
) {
}
