package com.se203730.mvc.generateuibe.config.web;

import com.se203730.mvc.generateuibe.common.resolver.CurrentUserUUIDArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Đăng ký argument resolver với Spring MVC để controller sử dụng được annotation @CurrentUserUUID.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserUUIDArgumentResolver currentUserUUIDArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserUUIDArgumentResolver);
    }
}
