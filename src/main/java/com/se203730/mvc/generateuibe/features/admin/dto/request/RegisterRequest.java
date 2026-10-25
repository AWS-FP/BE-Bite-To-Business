package com.se203730.mvc.generateuibe.features.admin.dto.request;

import com.se203730.mvc.generateuibe.features.user.enums.CustomerType;
import com.se203730.mvc.generateuibe.common.util.PhoneNumberUtils;
import jakarta.validation.constraints.*;

/**
 * Dữ liệu Guest gửi để đăng ký Customer; chỉ nhận thông tin hồ sơ, không nhận role hoặc mật khẩu do client tự chọn.
 */
public record RegisterRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*")
        String firstName,
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*")
        String lastName,
        @NotBlank
        @Size(max = 254)
        @Email
        @Pattern(regexp = "(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@gmail\\.com")
        String email,
        @NotBlank
        @Pattern(regexp = "\\+84[35789][0-9]{8}")
        String phone,
        @NotNull
        CustomerType customerType,
        @NotBlank
        @Size(max = 200)
        String businessName,
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "Thành phố Hà Nội|Thành phố Hồ Chí Minh")
        String province,
        // Dia chi hanh chinh moi chi con thanh pho va phuong/xa; quan/huyen cu co the NULL.
        @Size(max = 100)
        String district,
        @NotBlank
        @Size(max = 100)
        String ward,
        @NotBlank
        @Size(max = 1000)
        String deliveryAddress,
        @Size(max = 1000)
        String deliveryNote,
        // FE gửi rõ true hoặc false; không âm thầm đổi null/thiếu trường thành false.
        @NotNull
        Boolean invoiceSameAsDelivery,
        @Size(max = 200)
        String invoicePurchaser,
        @Pattern(regexp = "[0-9]{10}")
        String invoiceTaxCode,
        @Size(max = 254)
        @Email
        @Pattern(regexp = "(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@gmail\\.com")
        String invoiceEmail,
        @Size(max = 1000)
        String invoiceAddress
) {
    // Chuẩn hóa form trước validation; bỏ khoảng trắng, đưa email về chữ thường và đổi chuỗi rỗng thành null.
    public RegisterRequest {
        firstName = trim(firstName);
        lastName = trim(lastName);
        phone = PhoneNumberUtils.normalize(phone);
        email = normalizeEmail(email);
        businessName = trim(businessName);
        province = trim(province);
        district = trim(district);
        ward = trim(ward);
        deliveryAddress = trim(deliveryAddress);
        deliveryNote = trim(deliveryNote);
        invoicePurchaser = trim(invoicePurchaser);
        invoiceTaxCode = trim(invoiceTaxCode);
        invoiceEmail = normalizeEmail(invoiceEmail);
        invoiceAddress = trim(invoiceAddress);
    }

    public static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    // Chỉ chuẩn hóa chuỗi; không kiểm chứng hộp thư tồn tại. Bước nhập OTP mới xác minh quyền truy cập email.
    public static String normalizeEmail(String value) {
        String normalized = trim(value);
        return normalized == null ? null : normalized.toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}
