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
        Boolean invoiceSameAsDelivery,
        String invoicePurchaser,
        String invoiceTaxCode,
        String invoiceEmail,
        String invoiceAddress,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerProfileResponse from(User user, UserProfile profile) {
        String invoiceAddress = Boolean.TRUE.equals(profile.getInvoiceSameAsDelivery())
                ? profile.getFullDeliveryAddress()
                : profile.getInvoiceAddress();

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
                profile.getInvoiceSameAsDelivery(),
                profile.getInvoicePurchaser(),
                profile.getInvoiceTaxCode(),
                profile.getInvoiceEmail(),
                invoiceAddress,
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
