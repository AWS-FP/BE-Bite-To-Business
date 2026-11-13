package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.request.CreateRefundRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.RefundRequestResponse;
import com.se203730.mvc.generateuibe.features.customer.service.CustomerRefundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer/orders/{orderId}/refund-requests")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
@Tag(name = "Customer - Refund Requests")
public class CustomerRefundController {
    private final CustomerRefundService refunds;

    @PostMapping
    public ResponseEntity<ResponseObject<List<RefundRequestResponse>>> create(
            @CurrentUserUUID UUID customerId,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(201, "Refund request received.", refunds.create(customerId, orderId, request)));
    }

    @GetMapping
    public ResponseEntity<ResponseObject<List<RefundRequestResponse>>> list(
            @CurrentUserUUID UUID customerId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", refunds.list(customerId, orderId)));
    }
}
