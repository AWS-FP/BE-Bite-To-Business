package com.se203730.mvc.generateuibe.features.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.config.integration.SupabaseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lớp gọi HTTP API Supabase Auth, chuyển JSON thành đối tượng Java và quy đổi lỗi nhà cung cấp sang ErrorCode của ứng dụng.
 */
@Component
public class SupabaseAuthClient {
    // Chọn đúng RestClient đã cấu hình URL và timeout dành cho Supabase Auth.
    @Autowired
    @Qualifier("supabaseAuthRestClient")
    private RestClient client;

    @Autowired
    private SupabaseProperties properties;

    // Đối tượng tự tạo trong lớp, không phải dependency do Spring inject.
    private final SecureRandom random = new SecureRandom();

    /** Tạo tài khoản chờ duyệt trên Supabase; Customer chưa có mật khẩu do chính họ đặt. */
    public UUID createPendingUser(String email) {
        // 1. Supabase cần một mật khẩu ban đầu. Tạo ngẫu nhiên và không gửi mật khẩu này cho Customer.
        byte[] randomBytes = new byte[48];
        random.nextBytes(randomBytes);
        String randomText = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String temporaryPassword = "aA1!" + randomText;

        // 2. Các cặp key/value sẽ được RestClient chuyển thành JSON để gửi sang Supabase.
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("email", email);
        requestBody.put("email_confirm", false);
        requestBody.put("password", temporaryPassword);

        try {
            // 3. Chuẩn bị request POST và thêm key quản trị vào header.
            RestClient.RequestBodySpec request = client.post().uri("/admin/users");
            addAdminHeaders(request);
            request.contentType(MediaType.APPLICATION_JSON);
            request.body(requestBody);

            // 4. Gửi request, đọc JSON trả về thành record SupabaseUser.
            RestClient.ResponseSpec response = request.retrieve();
            SupabaseUser user = response.body(SupabaseUser.class);

            if (user == null || user.id() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }

            return user.id();
        } catch (RestClientResponseException exception) {
            // Supabase đã phản hồi, nhưng HTTP status là lỗi, ví dụ 400 hoặc 500.
            throw convertResponseError(exception, ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        } catch (RestClientException exception) {
            // Không gọi được dịch vụ hoặc không đọc được response, ví dụ lỗi kết nối/timeout.
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    /** Dọn tài khoản Auth vừa tạo nếu transaction lưu hồ sơ nội bộ thất bại. */
    public void deletePendingUser(UUID id) {
        try {
            // {id} được thay bằng UUID của tài khoản vừa tạo nhưng chưa lưu hồ sơ thành công.
            RestClient.RequestHeadersSpec<?> request = client.delete().uri("/admin/users/{id}", id);
            addAdminHeaders(request);

            RestClient.ResponseSpec response = request.retrieve();
            // API này không cần đọc nội dung JSON; chỉ cần biết request đã thành công.
            response.toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw convertResponseError(exception, ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    /** Dùng API quản trị lấy OTP; việc gửi mã qua email do RegistrationMailService thực hiện. */
    public GeneratedOtp generateOtp(String email) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("type", "magiclink");
        requestBody.put("email", email);

        try {
            RestClient.RequestBodySpec request = client.post().uri("/admin/generate_link");
            addAdminHeaders(request);
            request.contentType(MediaType.APPLICATION_JSON);
            request.body(requestBody);

            RestClient.ResponseSpec response = request.retrieve();
            GeneratedOtp generatedOtp = response.body(GeneratedOtp.class);

            if (generatedOtp == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }

            // Record chứa OTP do Supabase tạo; service sẽ gửi mã bằng email template của mình.
            return generatedOtp;
        } catch (RestClientResponseException exception) {
            throw convertResponseError(exception, ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    /** Gửi Gmail và OTP tới Supabase để đổi mã hợp lệ lấy phiên xác thực. */
    public Session verifyOtp(String email, String otp) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("type", "email");
        requestBody.put("email", email);
        requestBody.put("token", otp);

        try {
            RestClient.RequestBodySpec request = client.post().uri("/verify");
            addPublicHeaders(request);
            request.contentType(MediaType.APPLICATION_JSON);
            request.body(requestBody);

            RestClient.ResponseSpec response = request.retrieve();
            Session session = response.body(Session.class);

            if (session == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }

            return session;
        } catch (RestClientResponseException exception) {
            throw convertResponseError(exception, ErrorCode.INVALID_OTP);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    /** Đăng nhập bằng Gmail/password; Supabase kiểm tra mật khẩu và phát hành token. */
    public Session login(String email, String password) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("email", email);
        requestBody.put("password", password);

        try {
            RestClient.RequestBodySpec request = client.post().uri("/token?grant_type=password");
            addPublicHeaders(request);
            request.contentType(MediaType.APPLICATION_JSON);
            request.body(requestBody);

            RestClient.ResponseSpec response = request.retrieve();
            Session session = response.body(Session.class);

            if (session == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }

            return session;
        } catch (RestClientResponseException exception) {
            throw convertResponseError(exception, ErrorCode.INVALID_CREDENTIALS);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    /** Đổi refresh token lấy phiên mới từ Supabase. */
    public Session refresh(String refreshToken) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("refresh_token", refreshToken);

        try {
            RestClient.RequestBodySpec request = client.post().uri("/token?grant_type=refresh_token");
            addPublicHeaders(request);
            request.contentType(MediaType.APPLICATION_JSON);
            request.body(requestBody);

            RestClient.ResponseSpec response = request.retrieve();
            Session session = response.body(Session.class);

            if (session == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }

            return session;
        } catch (RestClientResponseException exception) {
            throw convertResponseError(exception, ErrorCode.UNAUTHORIZED);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    /** Dùng access token của chính người dùng để cập nhật mật khẩu của tài khoản đó. */
    public SupabaseUser setPassword(String accessToken, String password) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("password", password);

        try {
            RestClient.RequestBodySpec request = client.put().uri("/user");
            addPublicHeaders(request);
            // Bearer token cho Supabase biết tài khoản nào đang yêu cầu tạo mật khẩu.
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            request.contentType(MediaType.APPLICATION_JSON);
            request.body(requestBody);

            RestClient.ResponseSpec response = request.retrieve();
            SupabaseUser user = response.body(SupabaseUser.class);

            if (user == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }

            return user;
        } catch (RestClientResponseException exception) {
            throw convertResponseError(exception, ErrorCode.PASSWORD_POLICY_REJECTED);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    /** Thu hồi refresh token của phiên hiện tại; access token JWT đã cấp có thể còn hiệu lực đến khi hết hạn. */
    public void logout(String accessToken) {
        try {
            RestClient.RequestBodySpec request = client.post().uri("/logout?scope=local");
            addPublicHeaders(request);
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

            RestClient.ResponseSpec response = request.retrieve();
            response.toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw convertResponseError(exception, ErrorCode.UNAUTHORIZED);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
    }

    /** Gắn API secret key cho API quản trị; key này chỉ được giữ ở backend. */
    private void addAdminHeaders(RestClient.RequestHeadersSpec<?> request) {
        String key = properties.getSecretKey();
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_NOT_CONFIGURED);
        }

        request.header("apikey", key);
        // Legacy service_role key là JWT; sb_secret dùng qua header apikey.
        if (!key.startsWith("sb_secret_")) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + key);
        }
    }

    /** Gắn publishable key cho API đăng nhập, refresh hoặc xác minh OTP. */
    private void addPublicHeaders(RestClient.RequestHeadersSpec<?> request) {
        String key = properties.getPublishableKey();
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_NOT_CONFIGURED);
        }

        request.header("apikey", key);
    }

    /** Đổi lỗi HTTP của Supabase thành lỗi mà GlobalExceptionHandler của website hiểu được. */
    private BusinessException convertResponseError(RestClientResponseException exception, ErrorCode defaultError) {
        String providerCode = readProviderErrorCode(exception);
        int httpStatus = exception.getStatusCode().value();

        if (httpStatus == 429) {
            return new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        if (providerCode.equals("email_exists") || providerCode.equals("user_already_exists")) {
            return new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (providerCode.equals("otp_expired")) {
            return new BusinessException(ErrorCode.OTP_EXPIRED);
        }

        if (exception.getStatusCode().is5xxServerError()) {
            return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }

        // Ví dụ login nhận lỗi 400 không thuộc các mã trên thì dùng INVALID_CREDENTIALS.
        return new BusinessException(defaultError);
    }

    /** Chỉ đọc error_code đã biết; không đưa toàn bộ body lỗi có thể chứa dữ liệu nhạy cảm ra FE. */
    private String readProviderErrorCode(RestClientResponseException exception) {
        try {
            ProviderError error = exception.getResponseBodyAs(ProviderError.class);
            if (error != null && error.code() != null) {
                return error.code();
            }
        } catch (RuntimeException exceptionWhileReadingBody) {
            // Response lỗi có thể là HTML hoặc JSON không đúng mẫu; vẫn xử lý theo HTTP status.
            return "";
        }

        return "";
    }

    // Các record bên dưới ánh xạ JSON của Supabase; bỏ qua thuộc tính provider thêm mà ứng dụng không dùng.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SupabaseUser(
            UUID id,
            String email,
            @JsonProperty("email_confirmed_at") Instant emailConfirmedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeneratedOtp(UUID id, String email, @JsonProperty("email_otp") String emailOtp) {
        @Override
        public String toString() {
            return "GeneratedOtp[redacted]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    // @JsonProperty nối tên snake_case của Supabase với tên camelCase trong code Java.
    // Ví dụ JSON access_token được đọc bằng session.accessToken().
    public record Session(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            SupabaseUser user
    ) {
        // Tránh vô tình ghi access token và refresh token ra log khi in đối tượng.
        @Override
        public String toString() {
            return "Session[redacted]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProviderError(@JsonProperty("error_code") String code) {
    }
}
