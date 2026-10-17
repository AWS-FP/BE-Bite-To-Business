package com.se203730.mvc.generateuibe.features.admin.registration.dto.response;

import com.se203730.mvc.generateuibe.features.user.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.user.enums.CustomerType;
import com.se203730.mvc.generateuibe.features.auth.enums.RegistrationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin hồ sơ mà Admin cần xem: tài khoản, doanh nghiệp, địa chỉ, hóa đơn, kết quả duyệt và các mốc thời gian.
 */
public record RegistrationResponse(UUID id, UserResponse customer, CustomerType customerType,
        String businessName, String province, String district, String ward, String deliveryAddress,
        Boolean invoiceSameAsDelivery, String invoicePurchaser, String invoiceTaxCode,
        String invoiceEmail, String invoiceAddress, RegistrationStatus status, String reviewNote,
        Instant submittedAt, Instant reviewedAt, Instant otpSentAt, Instant activatedAt) {
}
