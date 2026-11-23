package com.se203730.mvc.generateuibe.features.customer.review.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.review.dto.response.ReviewSummaryResponse;
import com.se203730.mvc.generateuibe.features.customer.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Public rating summaries for many products at once, so catalog pages do not ask once per card. */
@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Catalog - Product Reviews")
public class ProductReviewSummaryController {
    private final ReviewService service;

    @GetMapping("/review-summaries")
    @Operation(summary = "Public: review count and average rating for up to 100 products")
    public ResponseEntity<ResponseObject<List<ReviewSummaryResponse>>> summaries(
            @RequestParam
            List<UUID> ids) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", service.summaries(ids)));
    }
}
