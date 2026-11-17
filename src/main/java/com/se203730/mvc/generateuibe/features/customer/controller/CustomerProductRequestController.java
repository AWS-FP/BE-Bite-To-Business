package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.logistics.service.ProductRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer/product-requests")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
@Tag(name = "Customer - Product Requests")
public class CustomerProductRequestController {
    private final ProductRequestService service;
    public record CreateRequest(@NotBlank @Size(max = 200) String productName,
                                @Size(max = 2000) String referenceUrl,
                                @Size(max = 2000) String note, boolean notifyByEmail) {}

    @PostMapping
    public ResponseEntity<ResponseObject<ProductRequestService.RequestView>> create(@CurrentUserUUID UUID userId,
            @Valid @RequestBody CreateRequest request) {
        var result = service.create(userId, request.productName(), request.referenceUrl(), request.note(),
                request.notifyByEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseObject<>(201, "Success", result));
    }
}
