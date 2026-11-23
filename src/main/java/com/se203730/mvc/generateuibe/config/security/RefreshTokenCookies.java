package com.se203730.mvc.generateuibe.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Refresh token sống trong cookie HttpOnly (JavaScript, kể cả mã XSS, không đọc được) thay vì localStorage/
 * sessionStorage. Cookie bền {@code maxAge} nên đóng tab/trình duyệt mở lại vẫn còn phiên; mỗi lần refresh
 * Supabase cấp token mới và cookie được đặt lại, nên phiên kéo dài tính từ lần dùng gần nhất.
 */
@Component
public class RefreshTokenCookies {
    public static final String NAME = "btb_refresh";
    private static final String PATH = "/api/auth";
    /** Access token cookie: sent to every API path, lives exactly as long as the JWT. */
    public static final String ACCESS_NAME = "btb_access";
    private static final String ACCESS_PATH = "/";

    private final Duration maxAge;
    private final boolean secure;
    private final String sameSite;
    private final Set<String> allowedOrigins;

    public RefreshTokenCookies(
            @Value("${app.auth.refresh-cookie-max-age:7d}")
            Duration maxAge,
            @Value("${app.auth.refresh-cookie-secure:true}")
            boolean secure,
            @Value("${app.auth.refresh-cookie-same-site:Lax}")
            String sameSite,
            @Value("${app.cors.allowed-origins:http://localhost:5173}")
            String allowedOrigins) {
        this.maxAge = maxAge;
        this.secure = secure;
        this.sameSite = sameSite;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String issue(String refreshToken) {
        return ResponseCookie.from(NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }

    public String clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH)
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }

    /** Access token cookie; expires together with the JWT so an expired token is never sent. */
    public String issueAccess(String accessToken, long expiresInSeconds) {
        return ResponseCookie.from(ACCESS_NAME, accessToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(ACCESS_PATH)
                .maxAge(Duration.ofSeconds(Math.max(0, expiresInSeconds)))
                .build()
                .toString();
    }

    public String clearAccess() {
        return ResponseCookie.from(ACCESS_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(ACCESS_PATH)
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }

    /**
     * Chống CSRF cho request dùng cookie: trình duyệt luôn gửi Origin với POST, và chỉ FE của mình được
     * phép. (SameSite=Lax đã chặn phần lớn; đây là lớp thứ hai.)
     */
    public boolean isAllowedOrigin(String origin) {
        return origin != null && allowedOrigins.contains(origin.trim());
    }
}
