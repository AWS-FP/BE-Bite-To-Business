package com.se203730.mvc.generateuibe.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Đọc cấu hình app.auth trong application properties để quản lý thời hạn OTP, thời gian chờ gửi lại và phiên tạo mật khẩu.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    // Khoảng thời gian backend chấp nhận OTP tính từ lúc gửi thành công.
    private Duration otpMaxAge = Duration.ofMinutes(10);
    // Thời gian tối thiểu giữa hai lần gửi OTP thành công.
    private Duration otpResendCooldown = Duration.ofSeconds(60);
    // Tuổi tối đa của phiên sau OTP được phép dùng để tạo mật khẩu.
    private Duration passwordSetupTtl = Duration.ofMinutes(10);
}
