package com.se203730.mvc.generateuibe.features.auth.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.user.entity.User;
import com.se203730.mvc.generateuibe.features.user.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.auth.dto.request.LoginRequest;
import com.se203730.mvc.generateuibe.features.auth.dto.request.RefreshTokenRequest;
import com.se203730.mvc.generateuibe.features.auth.dto.request.RegisterRequest;
import com.se203730.mvc.generateuibe.features.auth.dto.request.SetPasswordRequest;
import com.se203730.mvc.generateuibe.features.auth.dto.request.VerifyOtpRequest;
import com.se203730.mvc.generateuibe.features.auth.dto.response.AuthResponse;
import com.se203730.mvc.generateuibe.features.auth.dto.response.OtpVerificationResponse;
import com.se203730.mvc.generateuibe.features.auth.dto.response.RegistrationReceipt;
import com.se203730.mvc.generateuibe.features.auth.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.auth.service.AuthService;
import com.se203730.mvc.generateuibe.features.auth.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Authentication")
public class AuthController {
    // Spring tự gán các service vào những field có @Autowired khi tạo controller.
    @Autowired
    private AuthService authService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AccountAccessService accountAccessService;

    /** Guest gửi form chờ Admin duyệt; HTTP 201 chỉ báo đã tạo yêu cầu, chưa kích hoạt tài khoản. */
    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Guest gửi form đăng ký Customer để Admin xét duyệt")
    public ResponseEntity<ResponseObject<RegistrationReceipt>> register(@Valid @RequestBody RegisterRequest request) {
        // 1. Service lưu hồ sơ và yêu cầu chờ duyệt, trả biên nhận đăng ký.
        RegistrationReceipt data = registrationService.register(request);

        // 2. JSON trả cho FE có ba trường: status, message và data.
        ResponseObject<RegistrationReceipt> response = new ResponseObject<>(
                201,
                "Registration submitted. Please wait for Admin approval.",
                data
        );

        // 3. HTTP 201 báo đã tạo yêu cầu; no-store yêu cầu không cache response này.
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    /** Đăng nhập bằng Gmail/password sau khi đã hoàn thành duyệt, OTP và tạo mật khẩu. */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Đăng nhập bằng Gmail và mật khẩu")
    public ResponseEntity<ResponseObject<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse data = authService.login(request);

        ResponseObject<AuthResponse> response = new ResponseObject<>(
                200,
                "Signed in successfully.",
                data
        );

        // data chứa access token và refresh token, nên yêu cầu không lưu response vào cache.
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Nhận mã OTP từ form; trả token phục vụ bước tạo mật khẩu. */
    @PostMapping("/verify-otp")
    @SecurityRequirements
    @Operation(summary = "Xác minh OTP sau khi được duyệt và nhận token để tạo mật khẩu")
    public ResponseEntity<ResponseObject<OtpVerificationResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        OtpVerificationResponse data = registrationService.verifyOtp(request);

        ResponseObject<OtpVerificationResponse> response = new ResponseObject<>(
                200,
                "Email verified. Please set your password.",
                data
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Spring lấy JWT từ Bearer header và truyền vào tham số có @AuthenticationPrincipal. */
    @PostMapping("/activate")
    @Operation(summary = "Tạo mật khẩu ban đầu bằng token nhận được sau khi xác minh OTP")
    public ResponseEntity<ResponseObject<Void>> activate(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody SetPasswordRequest request) {
        // JWT lấy từ Authorization header; password và confirmPassword lấy từ JSON body.
        registrationService.activate(jwt, request);

        // Void biểu thị response này không có dữ liệu riêng; JSON data sẽ là null.
        ResponseObject<Void> response = new ResponseObject<>(
                200,
                "Account activated. Please sign in with your email and password.",
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Nhận refresh token để cấp lại phiên; không yêu cầu access token cũ còn hạn. */
    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Dùng refresh token để lấy phiên đăng nhập mới")
    public ResponseEntity<ResponseObject<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        AuthResponse data = authService.refresh(refreshToken);

        ResponseObject<AuthResponse> response = new ResponseObject<>(
                200,
                "Session refreshed.",
                data
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Đăng xuất phiên tương ứng với token mà người dùng đang gửi lên. */
    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất phiên hiện tại")
    public ResponseEntity<ResponseObject<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
        String accessToken = jwt.getTokenValue();
        authService.logout(accessToken);

        ResponseObject<Void> response = new ResponseObject<>(
                200,
                "Signed out successfully.",
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    /** Lấy tài khoản đang đăng nhập; @CurrentUserUUID lấy ID từ JWT thay vì query parameter. */
    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin tài khoản đang đăng nhập")
    public ResponseEntity<ResponseObject<UserResponse>> me(@CurrentUserUUID UUID id) {
        User user = accountAccessService.requireActiveUser(id);
        UserResponse data = UserResponse.from(user);

        ResponseObject<UserResponse> response = new ResponseObject<>(
                200,
                "Success",
                data
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }
}
