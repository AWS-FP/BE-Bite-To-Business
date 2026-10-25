package com.se203730.mvc.generateuibe.features.admin.registration.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.features.admin.registration.dto.request.ReviewRegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.registration.dto.response.RegistrationResponse;
import com.se203730.mvc.generateuibe.features.admin.registration.dto.response.RegistrationReviewResponse;
import com.se203730.mvc.generateuibe.features.admin.registration.dto.response.RegistrationStatsResponse;
import com.se203730.mvc.generateuibe.features.user.enums.CustomerType;
import com.se203730.mvc.generateuibe.features.auth.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.admin.registration.service.AdminRegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Các API dành cho Admin xem, duyệt, từ chối hồ sơ Customer và gửi lại OTP sau khi duyệt.
 */
@RestController
@RequestMapping("/api/admin/registration-requests")
@Tag(name = "Admin - Customer registration")
public class RegistrationAdminController {
    @Autowired
    private AdminRegistrationService registrations;

    /** Lọc danh sách hồ sơ theo trạng thái; page bắt đầu từ 0, size giới hạn tối đa 100. */
    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<RegistrationResponse>>> list(@CurrentUserUUID UUID adminId,
            @RequestParam(defaultValue = "PENDING") RegistrationStatus status,
            @RequestParam(defaultValue = "false") boolean allStatuses,
            @RequestParam(required = false) @Size(max = 254) String keyword,
            @RequestParam(required = false) CustomerType customerType,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<RegistrationResponse> registrationsPage = registrations.list(adminId, allStatuses ? null : status,
                keyword, customerType, direction, page, size);
        PageResponse<RegistrationResponse> data = PageResponse.from(registrationsPage);
        ResponseObject<PageResponse<RegistrationResponse>> response = new ResponseObject<>(200, "Success", data);

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<ResponseObject<RegistrationStatsResponse>> stats(@CurrentUserUUID UUID adminId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", registrations.stats(adminId)));
    }

    /** Xem chi tiết một yêu cầu đăng ký theo ID của yêu cầu, không phải ID của tài khoản. */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseObject<RegistrationResponse>> detail(@CurrentUserUUID UUID adminId, @PathVariable UUID id) {
        RegistrationResponse data = registrations.detail(adminId, id);
        ResponseObject<RegistrationResponse> response = new ResponseObject<>(200, "Success", data);

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    /** Nhận quyết định duyệt/từ chối của Admin; service kiểm tra quyền và trạng thái trước khi xử lý. */
    @PostMapping("/{id}/review")
    public ResponseEntity<ResponseObject<RegistrationReviewResponse>> review(@CurrentUserUUID UUID adminId,
            @PathVariable UUID id, @Valid @RequestBody ReviewRegistrationRequest request) {
        RegistrationReviewResponse data = registrations.review(adminId, id, request);
        String message = "Registration reviewed successfully.";

        // Chỉ chọn thông báo hiển thị; quyết định và xử lý gửi OTP nằm trong service.
        if (data.registration().status() == RegistrationStatus.APPROVED && !data.otpSent()) {
            message = "Approved, but OTP delivery failed. Use resend-otp to retry.";
        }

        ResponseObject<RegistrationReviewResponse> response = new ResponseObject<>(200, message, data);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    /** Gửi lại mã cho hồ sơ đã duyệt nếu lần gửi trước lỗi hoặc Customer cần mã mới. */
    @PostMapping("/{id}/resend-otp")
    public ResponseEntity<ResponseObject<RegistrationResponse>> resend(@CurrentUserUUID UUID adminId, @PathVariable UUID id) {
        RegistrationResponse data = registrations.resendOtp(adminId, id);
        ResponseObject<RegistrationResponse> response = new ResponseObject<>(200, "OTP sent.", data);

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
