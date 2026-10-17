package com.se203730.mvc.generateuibe.config.integration;

import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Tạo RestClient dùng chung để gọi Supabase Auth; đặt sẵn địa chỉ gốc và timeout cho các HTTP request.
 */
@Configuration
@EnableConfigurationProperties({SupabaseProperties.class, AuthProperties.class})
public class SupabaseClientConfig {
    @Bean
    RestClient supabaseAuthRestClient(SupabaseProperties properties) {
        // Timeout kết nối và đọc response giúp request không chờ Supabase vô thời hạn.
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getUrl().replaceAll("/+$", "") + "/auth/v1")
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
