package com.se203730.mvc.generateuibe.features.delivery.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.ClaimedPackageResponse;
import com.se203730.mvc.generateuibe.features.delivery.service.DeliveryPackageClaimService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/delivery/packages")
@PreAuthorize("hasRole('DELIVERY_STAFF')")
@RequiredArgsConstructor
@Tag(name = "Delivery - Packages")
public class DeliveryPackageClaimController {
    private final DeliveryPackageClaimService service;

    @GetMapping("/mine")
    public ResponseEntity<ResponseObject<PageResponse<ClaimedPackageResponse>>> mine(
            @CurrentUserUUID UUID staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<ClaimedPackageResponse> data = PageResponse.from(service.mine(staffId, page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ResponseObject<>(200, "Success", data));
    }

    @PostMapping("/{packageId}/claim")
    public ResponseEntity<ResponseObject<ClaimedPackageResponse>> claim(
            @CurrentUserUUID UUID staffId,
            @PathVariable UUID packageId) {
        ClaimedPackageResponse data = service.claim(staffId, packageId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ResponseObject<>(200, "Success", data));
    }
}
