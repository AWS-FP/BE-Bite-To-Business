package com.se203730.mvc.generateuibe.common.util;

/** Chuẩn hóa số di động Việt Nam trước khi kiểm tra trùng và lưu database. */
public class PhoneNumberUtils {
    private PhoneNumberUtils() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        // Bỏ dấu phân cách khi người dùng nhập hoặc dán số điện thoại.
        String phone = value.replaceAll("(?U)[\\s()-]", "");
        if (phone.isEmpty()) {
            return null;
        }

        if (phone.matches("0[35789][0-9]{8}")) {
            return "+84" + phone.substring(1);
        }

        // Form có sẵn nhãn +84 nên cho phép nhập chín chữ số còn lại.
        if (phone.matches("[35789][0-9]{8}")) {
            return "+84" + phone;
        }

        // Giữ số sai định dạng để validation từ chối, không tự đoán hoặc cắt bớt số.
        return phone;
    }
}
