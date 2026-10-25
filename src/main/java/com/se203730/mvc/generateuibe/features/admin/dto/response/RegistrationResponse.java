package com.se203730.mvc.generateuibe.features.admin.dto.response;

import com.se203730.mvc.generateuibe.features.user.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.user.enums.CustomerType;
import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin hồ sơ mà Admin cần xem: tài khoản, doanh nghiệp, địa chỉ, hóa đơn, kết quả duyệt và các mốc thời gian.
 */
public record RegistrationResponse(
        // ID yêu cầu dùng cho API chi tiết, duyệt và gửi lại OTP.
        UUID id,

        // Tài khoản: ID Customer, họ tên, Gmail, số điện thoại và trạng thái xác minh email.
        UserResponse customer,

        // Thông tin Customer và địa chỉ nhận hàng đã điền trong form đăng ký.
        CustomerType customerType,
        String businessName,
        String province,
        String district,
        String ward,
        String deliveryAddress,
        String fullDeliveryAddress,
        String deliveryNote,

        // Các trường hóa đơn có thể null nếu người đăng ký chưa cung cấp.
        Boolean invoiceSameAsDelivery,
        String invoicePurchaser,
        String invoiceTaxCode,
        String invoiceEmail,
        String invoiceAddress,

        // Kết quả xét duyệt; reviewNote lưu ghi chú, bắt buộc có lý do khi từ chối.
        RegistrationStatus status,
        String reviewNote,

        // Các mốc chưa diễn ra trả null. Đã duyệt không đồng nghĩa đã kích hoạt tài khoản.
        Instant submittedAt,
        Instant reviewedAt,
        Instant otpSentAt,
        Instant otpResendAvailableAt,
        Instant activatedAt
) {
}
