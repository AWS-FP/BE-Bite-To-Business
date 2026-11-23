package com.se203730.mvc.generateuibe.features.customer.dto.response;

import com.se203730.mvc.generateuibe.features.admin.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.admin.enums.CustomerType;

import java.time.Instant;

public record CustomerProfileResponse(
        UserResponse user,
        CustomerType customerType,
        String businessName,
        String province,
        String district,
        String ward,
        String deliveryAddress,
        String fullDeliveryAddress,
        String deliveryNote,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerProfileResponse from(User user, UserProfile profile) {
        return new CustomerProfileResponse(
                UserResponse.from(user),
                profile.getCustomerType(),
                profile.getBusinessName(),
                profile.getProvince(),
                profile.getDistrict(),
                profile.getWard(),
                profile.getDeliveryAddress(),
                profile.getFullDeliveryAddress(),
                profile.getDeliveryNote(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
