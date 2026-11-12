package com.se203730.mvc.generateuibe.features.delivery.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.ReadyDeliveryOrderResponse;
import com.se203730.mvc.generateuibe.features.delivery.service.ReadyDeliveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/delivery/ready-orders")
@PreAuthorize("hasRole('DELIVERY_STAFF')")
@RequiredArgsConstructor
@Tag(name = "Delivery - Ready Orders")
public class ReadyDeliveryController {
    private final ReadyDeliveryService service;

    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<ReadyDeliveryOrderResponse>>> list(
            @CurrentUserUUID UUID staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var result = PageResponse.from(service.list(staffId, page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", result));
    }
}
