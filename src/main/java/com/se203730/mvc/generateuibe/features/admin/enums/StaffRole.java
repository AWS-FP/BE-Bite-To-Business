package com.se203730.mvc.generateuibe.features.admin.enums;

import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;

public enum StaffRole {
    ECOMMERCE_EXECUTIVE, LOGISTICS_STAFF, DELIVERY_STAFF;

    public UserRole userRole() {
        return UserRole.valueOf(name());
    }
}
