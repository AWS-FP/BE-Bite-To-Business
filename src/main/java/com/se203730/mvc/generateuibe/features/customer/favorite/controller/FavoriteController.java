package com.se203730.mvc.generateuibe.features.customer.favorite.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.favorite.dto.response.FavoriteProductResponse;
import com.se203730.mvc.generateuibe.features.customer.favorite.dto.response.FavoriteStatusResponse;
import com.se203730.mvc.generateuibe.features.customer.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customer/favorites")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customer - Favorites")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService service;

    @GetMapping
    @Operation(summary = "List my favorite published products")
    public ResponseEntity<ResponseObject<PageResponse<FavoriteProductResponse>>> list(
            @CurrentUserUUID UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.list(customerId, page, size)));
    }

    @GetMapping("/{productId}/status")
    @Operation(summary = "Check whether I liked a product")
    public ResponseEntity<ResponseObject<FavoriteStatusResponse>> status(
            @CurrentUserUUID UUID customerId, @PathVariable UUID productId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.status(customerId, productId)));
    }

    @PostMapping("/{productId}")
    @Operation(summary = "Like a published product; idempotent")
    public ResponseEntity<ResponseObject<FavoriteStatusResponse>> add(
            @CurrentUserUUID UUID customerId, @PathVariable UUID productId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Product liked.", service.add(customerId, productId)));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Unlike a product; idempotent")
    public ResponseEntity<ResponseObject<FavoriteStatusResponse>> remove(
            @CurrentUserUUID UUID customerId, @PathVariable UUID productId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Product unliked.", service.remove(customerId, productId)));
    }
}
