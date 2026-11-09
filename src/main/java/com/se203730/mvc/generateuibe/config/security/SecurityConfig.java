
package com.se203730.mvc.generateuibe.config.security;

import com.se203730.mvc.generateuibe.common.security.RestSecurityExceptionHandler;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Quy định API nào công khai, API nào cần đăng nhập hoặc quyền Admin,
 * và cách xác minh JWT từ Supabase.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        CorsConfigurationSource corsConfigurationSource,
        AccountAccessService accounts,
        RestSecurityExceptionHandler securityErrors,
        @Value("${app.docs.public-enabled:false}")
        boolean docsPublicEnabled) throws Exception {

        http.cors(cors -> cors.configurationSource(corsConfigurationSource));

        // Các API dùng Bearer token trong header,
        // không xác thực bằng cookie.
        http.csrf(AbstractHttpConfigurer::disable);

        http.sessionManagement(
            session -> session.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS
            )
        );

        http.requestCache(cache -> cache.disable());

        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint(securityErrors)
            .accessDeniedHandler(securityErrors));

        // Khai báo route công khai trước;
        // các route còn lại phải vượt qua kiểm tra tài khoản.
        http.authorizeHttpRequests(authorize -> {

            authorize.requestMatchers(
                HttpMethod.GET,
                "/api/health"
            ).permitAll();

            // [MAIN] API ước tính giao hàng
            authorize.requestMatchers(
                HttpMethod.GET,
                "/api/delivery-estimate/**"
            ).permitAll();

            // [FEAT/HOME-PAGE] API danh mục sản phẩm
            authorize.requestMatchers(
                HttpMethod.GET,
                "/api/catalog/categories",
                "/api/catalog/categories/**"
            ).permitAll();

            // [FEAT/HOME-PAGE] API thương hiệu
            authorize.requestMatchers(
                HttpMethod.GET,
                "/api/catalog/brands",
                "/api/catalog/brands/**"
            ).permitAll();

            authorize.requestMatchers(
                HttpMethod.GET,
                "/api/catalog/products",
                "/api/catalog/products/*"
            ).permitAll();


            authorize.requestMatchers(
                HttpMethod.POST,
                "/api/auth/register",
                "/api/auth/login",
                "/api/auth/verify-otp",
                "/api/auth/refresh"
            ).permitAll();

            // Webhook dùng chữ ký HMAC của SePay
            // thay cho JWT của khách hàng.
            authorize.requestMatchers(
                HttpMethod.POST,
                "/api/payments/sepay/webhook"
            ).permitAll();

            // Phiên OTP chưa kích hoạt chỉ được
            // đi vào bước tạo mật khẩu.
            authorize.requestMatchers(
                HttpMethod.POST,
                "/api/auth/activate"
            ).authenticated();

            // Chỉ mở Swagger khi cấu hình môi trường cho phép.
            if (docsPublicEnabled) {
                authorize.requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml"
                ).permitAll();
            }

            // API quản trị cần ADMIN.
            authorize.requestMatchers("/api/admin/**")
                .access((authentication, context) ->
                    accounts.authorize(
                        authentication.get(),
                        true
                    )
                );

            // API khác phải có tài khoản đang hoạt động.
            authorize.anyRequest()
                .access((authentication, context) ->
                    accounts.authorize(
                        authentication.get(),
                        false
                    )
                );
        });

        // Spring xác minh JWT qua cấu hình JWKS.
        JwtAuthenticationConverter converter =
            new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
            accounts::authorities
        );

        http.oauth2ResourceServer(resourceServer -> resourceServer
            .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
            .authenticationEntryPoint(securityErrors)
            .accessDeniedHandler(securityErrors));

        return http.build();
    }
}
