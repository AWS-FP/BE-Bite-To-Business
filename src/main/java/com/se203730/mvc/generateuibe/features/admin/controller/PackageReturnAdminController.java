package com.se203730.mvc.generateuibe.features.admin.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.repository.PackageReturnRepository.ReturnView;
import com.se203730.mvc.generateuibe.features.customer.service.PackageReturnService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/admin/package-returns")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Package Returns")
public class PackageReturnAdminController {
    private final PackageReturnService returns;

    public record RejectRequest(@Size(max = 2000) String note) {}

    private static <T> ResponseEntity<ResponseObject<T>> ok(T data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", data));
    }

    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<ReturnView>>> list(
            @CurrentUserUUID UUID adminId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(returns.list(adminId, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseObject<ReturnView>> detail(
            @CurrentUserUUID UUID adminId,
            @PathVariable UUID id) {
        return ok(returns.detail(adminId, id));
    }

    @PatchMapping("/{id}/accept")
    public ResponseEntity<ResponseObject<ReturnView>> accept(
            @CurrentUserUUID UUID adminId,
            @PathVariable UUID id) {
        return ok(returns.accept(adminId, id));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ResponseObject<ReturnView>> reject(
            @CurrentUserUUID UUID adminId,
            @PathVariable UUID id,
            @RequestBody RejectRequest request) {
        return ok(returns.reject(adminId, id, request.note()));
    }
}
