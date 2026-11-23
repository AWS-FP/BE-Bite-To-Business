package com.se203730.mvc.generateuibe.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Reads the access token from the {@code Authorization: Bearer} header first (Swagger, the short-lived
 * OTP/password-setup tokens), otherwise from the HttpOnly {@code btb_access} cookie set at login, so the
 * browser app never holds the token in JavaScript.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {
    private final DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String fromHeader = header.resolve(request);
        if (fromHeader != null) return fromHeader;
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (RefreshTokenCookies.ACCESS_NAME.equals(cookie.getName())
                    && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
