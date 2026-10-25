package com.se203730.mvc.generateuibe.features.user.enums;

/**
 * Các role được lưu trong database để backend kiểm tra quyền; Guest là người chưa đăng nhập nên không có trong enum này.
 */
public enum UserRole {
    ADMIN, CUSTOMER, ECOMMERCE_EXECUTIVE, LOGISTICS_STAFF, DELIVERY_STAFF
}
