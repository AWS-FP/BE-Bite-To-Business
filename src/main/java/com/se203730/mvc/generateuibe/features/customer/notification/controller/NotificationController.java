package com.se203730.mvc.generateuibe.features.customer.notification.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.notification.dto.response.*;
import com.se203730.mvc.generateuibe.features.customer.notification.dto.request.NotificationPreferencesRequest;
import jakarta.validation.Valid;
import com.se203730.mvc.generateuibe.features.customer.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer/notifications")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
@Tag(name="Customer - Notifications")
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    @Operation(summary="My notifications, newest first; optional unreadOnly filter")
    public ResponseEntity<ResponseObject<PageResponse<NotificationResponse>>> list(
            @Parameter(hidden=true) @CurrentUserUUID UUID customerId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="false") boolean unreadOnly) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200,"Success",service.list(customerId,page,size,unreadOnly)));
    }
    @GetMapping("/preferences")
    @Operation(summary="Get my persisted notification preferences")
    public ResponseEntity<ResponseObject<NotificationPreferencesResponse>> preferences(
            @Parameter(hidden=true) @CurrentUserUUID UUID customerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200,"Success",service.preferences(customerId)));
    }
    @PutMapping("/preferences")
    @Operation(summary="Save my notification preferences")
    public ResponseEntity<ResponseObject<NotificationPreferencesResponse>> updatePreferences(
            @Parameter(hidden=true) @CurrentUserUUID UUID customerId,
            @Valid @RequestBody NotificationPreferencesRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200,"Success",service.updatePreferences(customerId,request)));
    }
    @GetMapping("/unread-count")
    @Operation(summary="Number of my unread notifications")
    public ResponseEntity<ResponseObject<NotificationUnreadResponse>> unreadCount(
            @Parameter(hidden=true) @CurrentUserUUID UUID customerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200,"Success",service.count(customerId)));
    }
    @PatchMapping("/{id}/read")
    @Operation(summary="Mark my notification read (idempotent)")
    public ResponseEntity<ResponseObject<NotificationMarkResponse>> markRead(
            @Parameter(hidden=true) @CurrentUserUUID UUID customerId,@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200,"Success",service.markRead(customerId,id)));
    }
    @PatchMapping("/read-all")
    @Operation(summary="Mark all my notifications read")
    public ResponseEntity<ResponseObject<NotificationMarkResponse>> markAll(
            @Parameter(hidden=true) @CurrentUserUUID UUID customerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200,"Success",service.markAll(customerId)));
    }
}
