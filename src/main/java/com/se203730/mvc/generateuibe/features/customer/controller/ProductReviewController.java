package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.response.ReviewPageResponse;
import com.se203730.mvc.generateuibe.features.customer.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/products/{productId}/reviews")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Catalog - Product Reviews")
public class ProductReviewController {
    private final ReviewService service;

    @GetMapping
    @Operation(summary = "Public: list product reviews with photos, rating average, pagination")
    public ResponseEntity<ResponseObject<ReviewPageResponse>> list(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", service.list(productId, page, size)));
    }
}
