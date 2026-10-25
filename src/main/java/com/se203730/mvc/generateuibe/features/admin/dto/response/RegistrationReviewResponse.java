package com.se203730.mvc.generateuibe.features.admin.dto.response;

/**
 * Kết quả xử lý và trạng thái gửi email tương ứng với quyết định của Admin.
 */
public record RegistrationReviewResponse(
        RegistrationResponse registration,

        // false khi từ chối hoặc khi đã duyệt nhưng gửi OTP lỗi; xem registration.status để phân biệt.
        boolean otpSent,
        boolean rejectionEmailSent
) {
}
