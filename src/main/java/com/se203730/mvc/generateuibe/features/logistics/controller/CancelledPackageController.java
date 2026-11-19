package com.se203730.mvc.generateuibe.features.logistics.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.logistics.service.CancelledPackageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@RestController
@RequestMapping("/api/logistics/cancelled-packages")
@PreAuthorize("hasRole('LOGISTICS_STAFF')")
@RequiredArgsConstructor
@Tag(name = "Logistics - Cancelled Packages")
public class CancelledPackageController {
    private final CancelledPackageService service;
    public record ProcessRequest(@Size(max = 1000) String note) {}

    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<CancelledPackageService.PackageView>>> list(@CurrentUserUUID UUID userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", PageResponse.from(service.list(userId, page, size))));
    }

    @GetMapping("/{packageId}")
    public ResponseEntity<ResponseObject<CancelledPackageService.PackageDetails>> detail(@CurrentUserUUID UUID userId,
            @PathVariable UUID packageId) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", service.detail(userId, packageId)));
    }

    @PostMapping("/{packageId}/release-reserved")
    public ResponseEntity<ResponseObject<CancelledPackageService.PackageDetails>> releaseReserved(@CurrentUserUUID UUID userId,
            @PathVariable UUID packageId, @Valid @RequestBody(required = false) ProcessRequest request) {
        String note = request == null ? null : request.note();
        return ResponseEntity.ok(new ResponseObject<>(200, "Reserved stock released.", service.releaseReserved(userId, packageId, note)));
    }
}
