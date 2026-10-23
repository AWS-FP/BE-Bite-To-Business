package com.se203730.mvc.generateuibe.common.security;

import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Trả lỗi JSON cho lỗi xác thực/phân quyền tại Security Filter, trước khi request đi tới controller.
 */
@Component
public class RestSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    @Autowired
    private ObjectMapper objectMapper;

    // Gọi khi chưa đăng nhập hoặc Bearer token không hợp lệ; trả HTTP 401 và header chuẩn cho Bearer authentication.
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        new BearerTokenAuthenticationEntryPoint().commence(request, response, ex);
        write(request, response, ErrorCode.UNAUTHORIZED);
    }

    // Gọi khi có xác thực nhưng tài khoản không đủ quyền hoặc không đạt điều kiện nghiệp vụ.
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, ex instanceof AccountAccessDeniedException denied
                ? denied.getErrorCode() : ErrorCode.ACCESS_DENIED);
    }

    // Tạo cùng cấu trúc ErrorResponse như lỗi MVC và yêu cầu trình duyệt không cache phản hồi này.
    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code.getStatus().value(),
                code.name(), code.getMessage(), request.getRequestURI(), List.of(), Instant.now()));
    }
}
