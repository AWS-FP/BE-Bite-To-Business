package com.se203730.mvc.generateuibe.config.integration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Đọc cấu hình app.supabase: địa chỉ Auth API, API key và timeout; giá trị nhạy cảm nằm ở local hoặc biến môi trường.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.supabase")
public class SupabaseProperties {
    private String url;
    // Key gọi API Auth thông thường; đây không phải secret dùng để ký JWT.
    private String publishableKey = "";
    // Key quản trị dùng để tạo tài khoản và lấy OTP; không trả về frontend.
    private String secretKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);
}
