package com.se203730.mvc.generateuibe.features.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.config.integration.SupabaseProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lớp gọi HTTP API Supabase Auth, chuyển JSON thành đối tượng Java và quy đổi lỗi nhà cung cấp sang ErrorCode của ứng dụng.
 */
@Component
public class SupabaseAuthClient {
    private final RestClient client;
    private final SupabaseProperties properties;
    private final SecureRandom random = new SecureRandom();

    public SupabaseAuthClient(@Qualifier("supabaseAuthRestClient") RestClient client, SupabaseProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** Tạo Auth user chưa xác minh bằng mật khẩu kỹ thuật ngẫu nhiên; mật khẩu này không gửi cho Customer. */
    public UUID createPendingUser(String email) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        SupabaseUser user = call(() -> admin(client.post().uri("/admin/users"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "email_confirm", false,
                        "password", "aA1!" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)))
                .retrieve().body(SupabaseUser.class), ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        if (user.id() == null) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        return user.id();
    }

    /** Dọn tài khoản Auth vừa tạo nếu transaction lưu hồ sơ nội bộ thất bại. */
    public void deletePendingUser(UUID id) {
        call(() -> { admin(client.delete().uri("/admin/users/{id}", id)).retrieve().toBodilessEntity(); return true; },
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    /** Dùng API quản trị lấy OTP; việc gửi mã qua email do RegistrationMailService thực hiện. */
    public GeneratedOtp generateOtp(String email) {
        return call(() -> admin(client.post().uri("/admin/generate_link"))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("type", "magiclink", "email", email))
                .retrieve().body(GeneratedOtp.class), ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    /** Gửi Gmail và OTP tới Supabase để đổi mã hợp lệ lấy phiên xác thực. */
    public Session verifyOtp(String email, String otp) {
        return call(() -> publicApi(client.post().uri("/verify"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "email", "email", email, "token", otp))
                .retrieve().body(Session.class), ErrorCode.INVALID_OTP);
    }

    /** Đăng nhập bằng Gmail/password; Supabase kiểm tra mật khẩu và phát hành token. */
    public Session login(String email, String password) {
        return call(() -> publicApi(client.post().uri("/token?grant_type=password"))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("email", email, "password", password))
                .retrieve().body(Session.class), ErrorCode.INVALID_CREDENTIALS);
    }

    /** Đổi refresh token lấy phiên mới từ Supabase. */
    public Session refresh(String refreshToken) {
        return call(() -> publicApi(client.post().uri("/token?grant_type=refresh_token"))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("refresh_token", refreshToken))
                .retrieve().body(Session.class), ErrorCode.UNAUTHORIZED);
    }

    /** Dùng access token của chính người dùng để cập nhật mật khẩu của tài khoản đó. */
    public SupabaseUser setPassword(String accessToken, String password) {
        return call(() -> publicApi(client.put().uri("/user"))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("password", password))
                .retrieve().body(SupabaseUser.class), ErrorCode.PASSWORD_POLICY_REJECTED);
    }

    /** Thu hồi refresh token của phiên hiện tại; access token JWT đã cấp có thể còn hiệu lực đến khi hết hạn. */
    public void logout(String accessToken) {
        call(() -> { publicApi(client.post().uri("/logout?scope=local"))
                .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().toBodilessEntity(); return true; },
                ErrorCode.UNAUTHORIZED);
    }

    /** Gắn API secret key cho API quản trị; key này chỉ được giữ ở backend. */
    private <T extends RestClient.RequestHeadersSpec<?>> T admin(T request) {
        String key = properties.getSecretKey();
        if (key == null || key.isBlank()) throw new BusinessException(ErrorCode.AUTH_NOT_CONFIGURED);
        request.header("apikey", key);
        // Legacy service_role key là JWT; sb_secret dùng qua header apikey.
        if (!key.startsWith("sb_secret_")) request.headers(headers -> headers.setBearerAuth(key));
        return request;
    }

    /** Gắn publishable key cho API đăng nhập, refresh hoặc xác minh OTP. */
    private <T extends RestClient.RequestHeadersSpec<?>> T publicApi(T request) {
        String key = properties.getPublishableKey();
        if (key == null || key.isBlank()) throw new BusinessException(ErrorCode.AUTH_NOT_CONFIGURED);
        request.header("apikey", key);
        return request;
    }

    /** Thực thi HTTP request và chuyển lỗi Supabase về ErrorCode thống nhất, không đưa body lỗi nhạy cảm ra client. */
    private <T> T call(Supplier<T> operation, ErrorCode rejection) {
        try {
            T result = operation.get();
            if (result == null) throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            return result;
        } catch (RestClientResponseException ex) {
            String code = "";
            try {
                ProviderError error = ex.getResponseBodyAs(ProviderError.class);
                if (error != null && error.code() != null) code = error.code();
            } catch (RuntimeException ignored) { /* Chỉ dùng mã lỗi đã biết, không trả body upstream. */ }
            if (ex.getStatusCode().value() == 429) throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            if (code.equals("email_exists") || code.equals("user_already_exists"))
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            if (code.equals("otp_expired")) throw new BusinessException(ErrorCode.OTP_EXPIRED);
            if (ex.getStatusCode().is5xxServerError())
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            throw new BusinessException(rejection);
        } catch (RestClientException ex) {
            // Không đính kèm exception upstream vì có thể chứa token hoặc dữ liệu nhạy cảm.
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    // Các record bên dưới ánh xạ JSON của Supabase; bỏ qua thuộc tính provider thêm mà ứng dụng không dùng.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SupabaseUser(UUID id, String email, @JsonProperty("email_confirmed_at") Instant emailConfirmedAt) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeneratedOtp(UUID id, String email, @JsonProperty("email_otp") String emailOtp) {
        @Override public String toString() { return "GeneratedOtp[redacted]"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(@JsonProperty("access_token") String accessToken,
                          @JsonProperty("refresh_token") String refreshToken,
                          @JsonProperty("token_type") String tokenType,
                          @JsonProperty("expires_in") long expiresIn, SupabaseUser user) {
        // Tránh vô tình ghi access token và refresh token ra log khi in đối tượng.
        @Override public String toString() { return "Session[redacted]"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProviderError(@JsonProperty("error_code") String code) { }
}
