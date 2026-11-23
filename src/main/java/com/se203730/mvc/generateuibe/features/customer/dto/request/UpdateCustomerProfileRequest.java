package com.se203730.mvc.generateuibe.features.customer.dto.request;

import com.se203730.mvc.generateuibe.common.util.PhoneNumberUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;


public record UpdateCustomerProfileRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*")
        String firstName,
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*")
        String lastName,
        @NotBlank
        @Pattern(regexp = "\\+84[35789][0-9]{8}")
        String phone,
        @Size(max = 2048)
        @URL
        @Pattern(regexp = "https?://[^\\s]+")
        String avatarUrl,
        @NotBlank
        @Size(max = 200)
        String businessName,
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "Thành phố Hà Nội|Thành phố Hồ Chí Minh")
        String province,
        @NotBlank
        @Size(max = 100)
        String ward,
        @NotBlank
        @Size(max = 1000)
        String deliveryAddress,
        @Size(max = 1000)
        String deliveryNote
) {
    public UpdateCustomerProfileRequest {
        firstName = trim(firstName);
        lastName = trim(lastName);
        phone = PhoneNumberUtils.normalize(phone);
        avatarUrl = trim(avatarUrl);
        businessName = trim(businessName);
        province = trim(province);
        ward = trim(ward);
        deliveryAddress = trim(deliveryAddress);
        deliveryNote = trim(deliveryNote);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString() {
        return "UpdateCustomerProfileRequest[redacted]";
    }
}
