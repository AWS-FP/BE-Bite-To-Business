package com.se203730.mvc.generateuibe.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Cookies are sent by the browser automatically, so a state-changing request that is authenticated by
 * our auth cookies must come from our own frontend (CSRF protection on top of SameSite=Lax).
 * Requests with an Authorization header, or without our cookies (e.g. the SePay webhook), are untouched.
 */
public class CookieCsrfOriginFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final RefreshTokenCookies cookies;

    public CookieCsrfOriginFilter(RefreshTokenCookies cookies) {
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod())
                && request.getHeader(HttpHeaders.AUTHORIZATION) == null
                && hasAuthCookie(request)
                && !cookies.isAllowedOrigin(request.getHeader(HttpHeaders.ORIGIN))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"status\":403,\"code\":\"ACCESS_DENIED\",\"message\":\"Request origin is not allowed.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean hasAuthCookie(HttpServletRequest request) {
        Cookie[] all = request.getCookies();
        if (all == null) return false;
        for (Cookie cookie : all) {
            String name = cookie.getName();
            if (RefreshTokenCookies.ACCESS_NAME.equals(name) || RefreshTokenCookies.NAME.equals(name)) return true;
        }
        return false;
    }
}
