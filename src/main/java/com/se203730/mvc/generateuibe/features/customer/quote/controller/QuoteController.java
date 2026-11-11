package com.se203730.mvc.generateuibe.features.customer.quote.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.AddQuoteItemRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.QuoteAllocationRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.QuoteContactRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.QuotePackageRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.UpdateQuoteItemRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.response.QuoteResponse;
import com.se203730.mvc.generateuibe.features.customer.quote.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customer/quotes")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
@Tag(name = "Customer - Quotes")
public class QuoteController {
    private final QuoteService service;

    private ResponseEntity<ResponseObject<QuoteResponse>> ok(QuoteResponse quote) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", quote));
    }

    @PostMapping
    @Operation(summary = "Create an empty DRAFT quote")
    public ResponseEntity<ResponseObject<QuoteResponse>> create(@CurrentUserUUID UUID userId) {
        return ok(service.create(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get my quote with items, packages and allocations")
    public ResponseEntity<ResponseObject<QuoteResponse>> get(
            @CurrentUserUUID UUID userId, @PathVariable UUID id) {
        return ok(service.get(userId, id));
    }

    @PutMapping("/{id}/contact")
    @Operation(summary = "Save contact information for a draft quote")
    public ResponseEntity<ResponseObject<QuoteResponse>> updateContact(
            @CurrentUserUUID UUID userId, @PathVariable UUID id,
            @Valid @RequestBody QuoteContactRequest request) {
        return ok(service.updateContact(userId, id, request));
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Add published product with requested quantity")
    public ResponseEntity<ResponseObject<QuoteResponse>> addItem(
            @CurrentUserUUID UUID userId, @PathVariable UUID id,
            @Valid @RequestBody AddQuoteItemRequest request) {
        return ok(service.addItem(userId, id, request));
    }

    @PutMapping("/{id}/items/{itemId}")
    @Operation(summary = "Update quantity or note of a quote item")
    public ResponseEntity<ResponseObject<QuoteResponse>> updateItem(
            @CurrentUserUUID UUID userId, @PathVariable UUID id, @PathVariable UUID itemId,
            @Valid @RequestBody UpdateQuoteItemRequest request) {
        return ok(service.updateItem(userId, id, itemId, request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Delete a quote item and its package allocations")
    public ResponseEntity<ResponseObject<QuoteResponse>> deleteItem(
            @CurrentUserUUID UUID userId, @PathVariable UUID id, @PathVariable UUID itemId) {
        return ok(service.deleteItem(userId, id, itemId));
    }

    @PostMapping("/{id}/packages")
    @Operation(summary = "Create a delivery package for the draft quote")
    public ResponseEntity<ResponseObject<QuoteResponse>> addPackage(
            @CurrentUserUUID UUID userId, @PathVariable UUID id,
            @Valid @RequestBody QuotePackageRequest request) {
        return ok(service.addPackage(userId, id, request));
    }

    @PutMapping("/{id}/packages/{packageId}")
    @Operation(summary = "Update delivery date, address and package name")
    public ResponseEntity<ResponseObject<QuoteResponse>> updatePackage(
            @CurrentUserUUID UUID userId, @PathVariable UUID id, @PathVariable UUID packageId,
            @Valid @RequestBody QuotePackageRequest request) {
        return ok(service.updatePackage(userId, id, packageId, request));
    }

    @DeleteMapping("/{id}/packages/{packageId}")
    @Operation(summary = "Soft-cancel a package; its quantities become unallocated")
    public ResponseEntity<ResponseObject<QuoteResponse>> deletePackage(
            @CurrentUserUUID UUID userId, @PathVariable UUID id, @PathVariable UUID packageId) {
        return ok(service.deletePackage(userId, id, packageId));
    }

    @PutMapping("/{id}/packages/{packageId}/items/{itemId}")
    @Operation(summary = "Assign item quantity to one delivery package")
    public ResponseEntity<ResponseObject<QuoteResponse>> allocate(
            @CurrentUserUUID UUID userId, @PathVariable UUID id,
            @PathVariable UUID packageId, @PathVariable UUID itemId,
            @Valid @RequestBody QuoteAllocationRequest request) {
        return ok(service.allocate(userId, id, packageId, itemId, request));
    }

    @DeleteMapping("/{id}/packages/{packageId}/items/{itemId}")
    @Operation(summary = "Remove a package-item allocation")
    public ResponseEntity<ResponseObject<QuoteResponse>> removeAllocation(
            @CurrentUserUUID UUID userId, @PathVariable UUID id,
            @PathVariable UUID packageId, @PathVariable UUID itemId) {
        return ok(service.removeAllocation(userId, id, packageId, itemId));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a complete draft quote for executive approval")
    public ResponseEntity<ResponseObject<QuoteResponse>> submit(
            @CurrentUserUUID UUID userId, @PathVariable UUID id) {
        return ok(service.submit(userId, id));
    }
}
