package com.se203730.mvc.generateuibe.features.auth.enums;

/**
 * Trạng thái xét duyệt hồ sơ: chờ duyệt, đã duyệt hoặc từ chối; chỉ được quyết định khi đang PENDING.
 */
public enum RegistrationStatus {
    PENDING, APPROVED, REJECTED
}
