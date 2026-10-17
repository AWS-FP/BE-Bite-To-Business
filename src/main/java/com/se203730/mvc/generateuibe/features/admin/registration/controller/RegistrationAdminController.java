package com.se203730.mvc.generateuibe.features.admin.registration.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.features.admin.registration.dto.request.ReviewRegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.registration.dto.response.RegistrationResponse;
import com.se203730.mvc.generateuibe.features.admin.registration.dto.response.RegistrationReviewResponse;
import com.se203730.mvc.generateuibe.features.auth.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.auth.service.RegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Các API dành cho Admin xem, duyệt, từ chối hồ sơ Customer và gửi lại OTP sau khi duyệt.
 */
@RestController
@RequestMapping("/api/admin/registration-requests")
@RequiredArgsConstructor
@Tag(name = "Admin - Customer registration")
public class RegistrationAdminController {
    private final RegistrationService registrations;

    /** Lọc danh sách hồ sơ theo trạng thái; page bắt đầu từ 0, size giới hạn tối đa 100. */
    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<RegistrationResponse>>> list(@CurrentUserUUID UUID adminId,
            @RequestParam(defaultValue = "PENDING") RegistrationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", PageResponse.from(registrations.list(adminId, status, page, size))));
    }

    /** Xem chi tiết một yêu cầu đăng ký theo ID của yêu cầu, không phải ID của tài khoản. */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseObject<RegistrationResponse>> detail(@CurrentUserUUID UUID adminId, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", registrations.detail(adminId, id)));
    }

    /** Nhận quyết định duyệt/từ chối của Admin; service kiểm tra quyền và trạng thái trước khi xử lý. */
    @PostMapping("/{id}/review")
    public ResponseEntity<ResponseObject<RegistrationReviewResponse>> review(@CurrentUserUUID UUID adminId,
            @PathVariable UUID id, @Valid @RequestBody ReviewRegistrationRequest request) {
        var result = registrations.review(adminId, id, request);
        String message = result.registration().status() == RegistrationStatus.APPROVED && !result.otpSent()
                ? "Approved, but OTP delivery failed. Use resend-otp to retry."
                : "Registration reviewed successfully.";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ResponseObject<>(200, message, result));
    }

    /** Gửi lại mã cho hồ sơ đã duyệt nếu lần gửi trước lỗi hoặc Customer cần mã mới. */
    @PostMapping("/{id}/resend-otp")
    public ResponseEntity<ResponseObject<RegistrationResponse>> resend(@CurrentUserUUID UUID adminId, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "OTP sent.", registrations.resendOtp(adminId, id)));
    }
}
