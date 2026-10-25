package com.se203730.mvc.generateuibe.features.admin.registration.dto.response;

/**
 * Kết quả duyệt hồ sơ cùng cờ otpSent; đã duyệt thành công vẫn có thể cần gửi lại OTP nếu dịch vụ email lỗi.
 */
public record RegistrationReviewResponse(
        RegistrationResponse registration,

        // false khi từ chối hoặc khi đã duyệt nhưng gửi OTP lỗi; xem registration.status để phân biệt.
        boolean otpSent
) {
}
