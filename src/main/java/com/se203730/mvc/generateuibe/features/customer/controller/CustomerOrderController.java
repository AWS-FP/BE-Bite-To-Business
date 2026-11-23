package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.response.*;
import com.se203730.mvc.generateuibe.features.customer.service.CustomerOrderQueryService;
import com.se203730.mvc.generateuibe.features.customer.service.CustomerOrderCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customer/orders")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
@Tag(name = "Customer - My Orders")
public class CustomerOrderController {
    private final CustomerOrderQueryService service;
    private final CustomerOrderCancellationService cancellation;

    @GetMapping
    @Operation(summary = "List only the authenticated customer's orders, newest first")
    public ResponseEntity<ResponseObject<CustomerOrdersPageResponse>> list(
            @CurrentUserUUID UUID customerId,
            @RequestParam(defaultValue = "ALL") String group,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.list(customerId, group, page, size)));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel my order while it is still unpaid; reserved stock is returned")
    public ResponseEntity<ResponseObject<CustomerOrderDetailResponse>> cancel(
            @CurrentUserUUID UUID customerId, @PathVariable UUID orderId) {
        cancellation.cancel(customerId, orderId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Order cancelled.", service.get(customerId, orderId)));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Detail of my order with item price/VAT snapshots and delivery packages")
    public ResponseEntity<ResponseObject<CustomerOrderDetailResponse>> detail(
            @CurrentUserUUID UUID customerId, @PathVariable UUID orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.get(customerId, orderId)));
    }
}
