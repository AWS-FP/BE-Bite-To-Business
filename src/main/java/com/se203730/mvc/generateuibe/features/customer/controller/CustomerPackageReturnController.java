package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.request.CreatePackageReturnRequest;
import com.se203730.mvc.generateuibe.features.customer.repository.PackageReturnRepository.ReturnView;
import com.se203730.mvc.generateuibe.features.customer.service.PackageReturnService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer/orders/{orderId}/packages/{packageId}/return")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customer - Package Returns")
public class CustomerPackageReturnController {
    private final PackageReturnService returns;

    @PostMapping
    public ResponseEntity<ResponseObject<ReturnView>> request(
            @CurrentUserUUID UUID customerId,
            @PathVariable UUID orderId,
            @PathVariable UUID packageId,
            @Valid @RequestBody CreatePackageReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseObject<>(201, "Return request received.",
                        returns.request(customerId, orderId, packageId, request)));
    }
}
