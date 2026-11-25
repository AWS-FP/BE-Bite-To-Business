package com.se203730.mvc.generateuibe.features.admin.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.admin.dto.request.ResolvePaymentReviewRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.response.PaymentReviewResponse;
import com.se203730.mvc.generateuibe.features.customer.service.SepayReviewResolutionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Trang Admin "Thanh toán cần xử lý": khoản SePay đã vào tài khoản nhưng không tự khớp đơn. */
@RestController
@RequestMapping("/api/admin/payments/sepay")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Admin - Payment Reviews")
public class SepayReviewController {
    @Autowired
    private SepayReviewResolutionService reviews;

    @GetMapping("/review")
    public ResponseEntity<ResponseObject<PageResponse<PaymentReviewResponse>>> list(
            @RequestParam(defaultValue = "false")
            boolean resolved,
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,
            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size) {
        PageResponse<PaymentReviewResponse> data = PageResponse.from(reviews.list(resolved, page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", data));
    }

    @GetMapping("/review/count")
    public ResponseEntity<ResponseObject<Map<String, Long>>> count() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", Map.of("open", reviews.countOpen())));
    }

    @PostMapping("/review/{transactionId}/resolve")
    public ResponseEntity<ResponseObject<Void>> resolve(
            @CurrentUserUUID
            UUID adminId,
            @PathVariable
            Long transactionId,
            @Valid
            @RequestBody
            ResolvePaymentReviewRequest request) {
        reviews.resolve(adminId, transactionId, request);
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", null));
    }
}
