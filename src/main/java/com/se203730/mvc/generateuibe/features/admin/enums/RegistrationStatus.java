package com.se203730.mvc.generateuibe.features.admin.enums;

/**
 * Hồ sơ chuyển sang chờ kích hoạt sau khi admin xét duyệt, và chỉ APPROVED khi tạo mật khẩu thành công.
 */
public enum RegistrationStatus {
    PENDING, AWAITING_ACTIVATION, APPROVED, REJECTED
}
