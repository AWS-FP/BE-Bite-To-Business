package com.se203730.mvc.generateuibe.features.admin.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.request.ForgotPasswordRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.request.LoginRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.request.RefreshTokenRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.request.RegisterRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.request.SetPasswordRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.request.VerifyOtpRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.response.AuthResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.response.OtpVerificationResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.response.RegistrationReceipt;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.admin.service.AuthService;
import com.se203730.mvc.generateuibe.features.admin.service.ActivationOtpResendService;
import com.se203730.mvc.generateuibe.features.admin.service.PasswordResetService;
import com.se203730.mvc.generateuibe.features.admin.service.RegistrationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.config.security.RefreshTokenCookies;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Nhận HTTP request đăng ký, đăng nhập, OTP và kích hoạt; @Valid kiểm tra DTO trước khi chuyển cho service xử lý.
 * Mỗi hàm làm ba bước: gọi service, tạo JSON response, trả HTTP status và header.
 * Cache là bản sao response được trình duyệt/proxy lưu để dùng lại.
 * Header Cache-Control: no-store yêu cầu không lưu bản sao response auth có token/thông tin tài khoản vào cache.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Shared - Authentication")
public class AuthController {
    // Spring tự gán các service vào những field có @Autowired khi tạo controller.
    @Autowired
    private AuthService authService;

    @Autowired
    private ActivationOtpResendService activationResends;

    public record ResendReceipt(long retryAfterSeconds) {}

    @PostMapping("/resend-otp")
    @SecurityRequirements
    public ResponseEntity<ResponseObject<ResendReceipt>> resendOtp(@Valid @RequestBody ForgotPasswordRequest request) {
        long cooldown = activationResends.requestForEmail(request.email());
        return ResponseEntity.accepted().header("Cache-Control", "no-store").body(
                new ResponseObject<>(202, "Activation code request received.", new ResendReceipt(cooldown)));
    }

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AccountAccessService accountAccessService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private RefreshTokenCookies refreshCookies;

    /** Guest gửi form chờ Admin duyệt; HTTP 201 chỉ báo đã tạo yêu cầu, chưa kích hoạt tài khoản. */
    @PostMapping("/register")
    @SecurityRequirements
    public ResponseEntity<ResponseObject<RegistrationReceipt>> register(
            @Valid
            @RequestBody
            RegisterRequest request) {
        // 1. Service lưu hồ sơ và yêu cầu chờ duyệt, trả biên nhận đăng ký.
        RegistrationReceipt data = registrationService.register(request);

        // 2. JSON trả cho FE có ba trường: status, message và data.
        ResponseObject<RegistrationReceipt> response = new ResponseObject<>(
                201,
                "Registration submitted. Please wait for Admin approval.",
                data);

        // 3. HTTP 201 báo đã tạo yêu cầu; no-store yêu cầu không cache response này.
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    /** Đăng nhập bằng Gmail/password sau khi đã hoàn thành duyệt, OTP và tạo mật khẩu. */
    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<ResponseObject<AuthResponse>> login(
            @Valid
            @RequestBody
            LoginRequest request) {
        AuthResponse data = authService.login(request);

        ResponseObject<AuthResponse> response = new ResponseObject<>(
                200,
                "Signed in successfully.",
                withoutTokens(data));

        // data chứa access token, nên yêu cầu không lưu response vào cache.
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        // Refresh token chỉ đi trong cookie HttpOnly: JavaScript (kể cả XSS) và DevTools body không thấy.
        headers.add(HttpHeaders.SET_COOKIE, refreshCookies.issue(data.refreshToken()));
        headers.add(HttpHeaders.SET_COOKIE, refreshCookies.issueAccess(data.accessToken(), data.expiresIn()));

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Nhận mã OTP từ form; trả token phục vụ bước tạo mật khẩu. */
    @PostMapping("/verify-otp")
    @SecurityRequirements
    public ResponseEntity<ResponseObject<OtpVerificationResponse>> verifyOtp(
            @Valid
            @RequestBody
            VerifyOtpRequest request) {
        OtpVerificationResponse data = registrationService.verifyOtp(request);

        ResponseObject<OtpVerificationResponse> response = new ResponseObject<>(
                200,
                "Email verified. Please set your password.",
                data);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Spring lấy JWT từ Bearer header và truyền vào tham số có @AuthenticationPrincipal. */
    @PostMapping("/activate")
    public ResponseEntity<ResponseObject<Void>> activate(@AuthenticationPrincipal
    Jwt jwt,
            @Valid
            @RequestBody
            SetPasswordRequest request) {
        // JWT lấy từ Authorization header; password và confirmPassword lấy từ JSON body.
        registrationService.activate(jwt, request);

        // Void biểu thị response này không có dữ liệu riêng; JSON data sẽ là null.
        ResponseObject<Void> response = new ResponseObject<>(
                200,
                "Account activated. Please sign in with your email and password.",
                null);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Gửi OTP đặt lại mật khẩu tới Gmail; luôn trả cùng một thông báo dù Gmail có tồn tại hay không. */
    @PostMapping("/forgot-password")
    @SecurityRequirements
    public ResponseEntity<ResponseObject<Void>> forgotPassword(
            @Valid
            @RequestBody
            ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);

        ResponseObject<Void> response = new ResponseObject<>(
                200,
                "If the email belongs to an active account, a verification code has been sent.",
                null);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Xác minh OTP quên mật khẩu; trả token ngắn hạn chỉ dùng cho bước đặt mật khẩu mới. */
    @PostMapping("/forgot-password/verify-otp")
    @SecurityRequirements
    public ResponseEntity<ResponseObject<OtpVerificationResponse>> verifyPasswordResetOtp(
            @Valid
            @RequestBody
            VerifyOtpRequest request) {
        OtpVerificationResponse data = passwordResetService.verifyOtp(request);

        ResponseObject<OtpVerificationResponse> response = new ResponseObject<>(
                200,
                "Code verified. Please set a new password.",
                data);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Dùng Bearer token sau OTP để đặt mật khẩu mới; mọi phiên cũ bị thu hồi sau khi thành công. */
    @PostMapping("/reset-password")
    public ResponseEntity<ResponseObject<Void>> resetPassword(@AuthenticationPrincipal
    Jwt jwt,
            @Valid
            @RequestBody
            SetPasswordRequest request) {
        passwordResetService.resetPassword(jwt, request);

        ResponseObject<Void> response = new ResponseObject<>(
                200,
                "Password updated. Please sign in with your new password.",
                null);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Nhận refresh token để cấp lại phiên; không yêu cầu access token cũ còn hạn. */
    @PostMapping("/refresh")
    @SecurityRequirements
    public ResponseEntity<ResponseObject<AuthResponse>> refresh(
            @Valid
            @RequestBody(required = false)
            RefreshTokenRequest request,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false)
            String cookieToken,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false)
            String origin) {
        String refreshToken;
        if (cookieToken != null && !cookieToken.isBlank()) {
            // Cookie được trình duyệt tự gửi, nên chỉ nhận khi request đến từ FE của mình (chống CSRF).
            if (!refreshCookies.isAllowedOrigin(origin)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            refreshToken = cookieToken;
        } else if (request != null && request.refreshToken() != null) {
            // FE cũ gửi refresh token trong body; giữ trong giai đoạn chuyển đổi.
            refreshToken = request.refreshToken();
        } else {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        AuthResponse data = authService.refresh(refreshToken);

        ResponseObject<AuthResponse> response = new ResponseObject<>(
                200,
                "Session refreshed.",
                withoutTokens(data));

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        // Supabase xoay vòng refresh token: đặt lại cookie để phiên kéo dài thêm maxAge kể từ lần dùng này.
        headers.add(HttpHeaders.SET_COOKIE, refreshCookies.issue(data.refreshToken()));
        headers.add(HttpHeaders.SET_COOKIE, refreshCookies.issueAccess(data.accessToken(), data.expiresIn()));

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Đăng xuất phiên tương ứng với token mà người dùng đang gửi lên. */
    @PostMapping("/logout")
    public ResponseEntity<ResponseObject<Void>> logout(@AuthenticationPrincipal
    Jwt jwt) {
        String accessToken = jwt.getTokenValue();
        authService.logout(accessToken);

        ResponseObject<Void> response = new ResponseObject<>(
                200,
                "Signed out successfully.",
                null);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.add(HttpHeaders.SET_COOKIE, refreshCookies.clear());
        headers.add(HttpHeaders.SET_COOKIE, refreshCookies.clearAccess());

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Lấy tài khoản đang đăng nhập; @CurrentUserUUID lấy ID từ JWT thay vì query parameter. */
    @GetMapping("/me")
    public ResponseEntity<ResponseObject<UserResponse>> me(@CurrentUserUUID
    UUID id) {
        User user = accountAccessService.requireActiveUser(id);
        UserResponse data = UserResponse.from(user);

        ResponseObject<UserResponse> response = new ResponseObject<>(
                200,
                "Success",
                data);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Both tokens are delivered only as HttpOnly cookies, never in the JSON body (JS/XSS cannot read them). */
    private static AuthResponse withoutTokens(AuthResponse data) {
        return new AuthResponse(null, null, data.tokenType(), data.expiresIn(), data.user());
    }
}
