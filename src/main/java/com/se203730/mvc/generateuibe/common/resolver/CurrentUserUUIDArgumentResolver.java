package com.se203730.mvc.generateuibe.common.resolver;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Tự truyền UUID của người đang đăng nhập vào tham số có @CurrentUserUUID; không lấy ID do client tự gửi.
 */
@Component
public class CurrentUserUUIDArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserUUID.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    public @Nullable
    Object resolveArgument(
            MethodParameter parameter,
            @Nullable
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable
            WebDataBinderFactory binderFactory) {
        CurrentUserUUID annotation = parameter.getParameterAnnotation(CurrentUserUUID.class);
        boolean required = annotation == null || annotation.required();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            if (required) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return null;
        }

        // Chỉ đọc JWT đã được Spring Security xác thực, không đọc token thô hoặc userId từ FE.
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String subject = jwtAuthentication.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        try {
            UUID userId = UUID.fromString(subject);
            // UUID.fromString chấp nhận một số dạng rút gọn; sub phải đủ định dạng UUID.
            if (!userId.toString().equalsIgnoreCase(subject)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return userId;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, ex);
        }
    }
}
