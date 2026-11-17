package com.se203730.mvc.generateuibe.features.logistics.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.logistics.service.CancelledPackageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/logistics/cancelled-packages")
@PreAuthorize("hasRole('LOGISTICS_STAFF')")
@RequiredArgsConstructor
@Tag(name = "Logistics - Cancelled Packages")
public class CancelledPackageController {
    private final CancelledPackageService service;

    @GetMapping
    public ResponseEntity<ResponseObject<Page<CancelledPackageService.PackageView>>> list(@CurrentUserUUID UUID userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", service.list(userId, page, size)));
    }
}
