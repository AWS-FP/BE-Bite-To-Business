package com.se203730.mvc.generateuibe.features.logistics.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.logistics.service.ProductRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/logistics/product-requests")
@PreAuthorize("hasRole('LOGISTICS_STAFF')")
@RequiredArgsConstructor
@Tag(name = "Logistics - Product Requests")
public class LogisticsProductRequestController {
    private final ProductRequestService service;
    public record ResponseRequest(@NotBlank String decision, @NotBlank @Size(max = 2000) String note,
                                  UUID productId) {}

    @GetMapping
    public ResponseEntity<ResponseObject<Page<ProductRequestService.RequestView>>> list(@CurrentUserUUID UUID userId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", service.list(userId, status, page, size)));
    }

    @PostMapping("/{requestId}/start-review")
    public ResponseEntity<ResponseObject<ProductRequestService.RequestView>> startReview(@CurrentUserUUID UUID userId,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", service.startReview(userId, requestId)));
    }

    @PostMapping("/{requestId}/response")
    public ResponseEntity<ResponseObject<ProductRequestService.RequestView>> respond(@CurrentUserUUID UUID userId,
            @PathVariable UUID requestId, @Valid @RequestBody ResponseRequest request) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", service.respond(userId, requestId,
                request.decision(), request.note(), request.productId())));
    }
}
