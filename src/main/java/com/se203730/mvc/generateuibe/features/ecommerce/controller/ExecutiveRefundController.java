package com.se203730.mvc.generateuibe.features.ecommerce.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutiveRefundDecisionRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ExecutiveRefundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/ecommerce/refund-requests")
@PreAuthorize("hasRole('ECOMMERCE_EXECUTIVE')")
@Tag(name = "E-commerce - Customer Cancellation Requests")
public class ExecutiveRefundController {
    private final ExecutiveRefundService service;

    private static <T> ResponseEntity<ResponseObject<T>> ok(T data) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(new ResponseObject<>(200, "Success", data));
    }

    @GetMapping
    public ResponseEntity<ResponseObject<EcommercePageResponse<Map<String, Object>>>> list(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(service.list(status, page, size));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ResponseObject<Map<String, Object>>> approve(
        @PathVariable UUID id,
        @CurrentUserUUID UUID staffId,
        @Valid @RequestBody(required = false) ExecutiveRefundDecisionRequest request) {
        return ok(service.approve(id, staffId, request));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ResponseObject<Map<String, Object>>> reject(
        @PathVariable UUID id,
        @CurrentUserUUID UUID staffId,
        @Valid @RequestBody ExecutiveRefundDecisionRequest request) {
        return ok(service.reject(id, staffId, request));
    }

    @PatchMapping("/{id}/refunded")
    public ResponseEntity<ResponseObject<Map<String, Object>>> refunded(
        @PathVariable UUID id,
        @CurrentUserUUID UUID staffId,
        @Valid @RequestBody ExecutiveRefundDecisionRequest request) {
        return ok(service.markRefunded(id, staffId, request));
    }
}
