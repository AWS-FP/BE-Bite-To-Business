package com.se203730.mvc.generateuibe.config.security;

import com.se203730.mvc.generateuibe.common.security.RestSecurityExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;


import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Cấu hình Bearer JWT và các endpoint hạ tầng; quyền tài khoản được bổ sung cùng feature auth.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            RestSecurityExceptionHandler securityErrors,
            @Value("${app.docs.public-enabled:false}") boolean docsPublicEnabled) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        // Các API dùng Bearer token trong header, không xác thực bằng cookie.
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors.authenticationEntryPoint(securityErrors).accessDeniedHandler(securityErrors));

        // Khai báo route công khai trước; các route còn lại phải vượt qua kiểm tra tài khoản và phiên đăng nhập.
        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers(HttpMethod.GET, "/api/health").permitAll();
            // Chỉ mở Swagger khi cấu hình môi trường cho phép công khai tài liệu API.
            if (docsPublicEnabled) {
                authorize.requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml").permitAll();
            }
            authorize.anyRequest().authenticated();
        });

        // Feature auth bổ sung kiểm tra role và trạng thái tài khoản khi được tích hợp.
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(securityErrors).accessDeniedHandler(securityErrors));
        return http.build();
    }
}
