package com.se203730.mvc.generateuibe.features.auth.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.user.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.auth.dto.request.*;
import com.se203730.mvc.generateuibe.features.auth.dto.response.*;
import com.se203730.mvc.generateuibe.features.auth.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.auth.service.AuthService;
import com.se203730.mvc.generateuibe.features.auth.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Nhận HTTP request đăng ký, đăng nhập, OTP và kích hoạt; @Valid kiểm tra DTO trước khi chuyển cho service xử lý.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService auth;
    private final RegistrationService registrations;
    private final AccountAccessService accounts;

    /** Guest gửi form chờ Admin duyệt; HTTP 201 chỉ báo đã tạo yêu cầu, chưa kích hoạt tài khoản. */
    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Guest submits a Customer registration for Admin review")
    public ResponseEntity<ResponseObject<RegistrationReceipt>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(201, "Registration submitted. Please wait for Admin approval.",
                        registrations.register(request)));
    }

    /** Đăng nhập bằng Gmail/password sau khi đã hoàn thành duyệt, OTP và tạo mật khẩu. */
    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<ResponseObject<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Signed in successfully.", auth.login(request)));
    }

    /** Nhận mã OTP từ form; trả token phục vụ bước tạo mật khẩu. */
    @PostMapping("/verify-otp")
    @SecurityRequirements
    @Operation(summary = "Verify the OTP sent after approval; receive a token for password setup")
    public ResponseEntity<ResponseObject<OtpVerificationResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Email verified. Please set your password.", registrations.verifyOtp(request)));
    }

    /** Spring lấy JWT từ Bearer header và truyền vào tham số có @AuthenticationPrincipal. */
    @PostMapping("/activate")
    @Operation(summary = "Set the initial password using the access token returned by verify-otp")
    public ResponseEntity<ResponseObject<Void>> activate(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody SetPasswordRequest request) {
        registrations.activate(jwt, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Account activated. Please sign in with your email and password.", null));
    }

    /** Nhận refresh token để cấp lại phiên; không yêu cầu access token cũ còn hạn. */
    @PostMapping("/refresh")
    @SecurityRequirements
    public ResponseEntity<ResponseObject<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Session refreshed.", auth.refresh(request.refreshToken())));
    }

    /** Đăng xuất phiên tương ứng với token mà người dùng đang gửi lên. */
    @PostMapping("/logout")
    public ResponseEntity<ResponseObject<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
        auth.logout(jwt.getTokenValue());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Signed out successfully.", null));
    }

    /** Lấy tài khoản đang đăng nhập; @CurrentUserUUID lấy ID từ JWT thay vì query parameter. */
    @GetMapping("/me")
    public ResponseEntity<ResponseObject<UserResponse>> me(@CurrentUserUUID UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", UserResponse.from(accounts.requireActiveUser(id))));
    }
}
