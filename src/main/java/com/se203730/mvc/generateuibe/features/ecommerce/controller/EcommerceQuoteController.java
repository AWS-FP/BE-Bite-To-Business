package com.se203730.mvc.generateuibe.features.ecommerce.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteReviewResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.QuoteAddressReviewRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.QuoteDecisionRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.service.QuoteReviewService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ecommerce/quotes")
@PreAuthorize("hasRole('ECOMMERCE_EXECUTIVE')")
@Tag(name = "E-commerce - Quotes")
@Validated
public class EcommerceQuoteController {
    @Autowired
    private QuoteReviewService service;

    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<QuoteReviewResponse>>> list(
            @CurrentUserUUID UUID staffId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", PageResponse.from(service.listPending(staffId, page, size))));
    }

    @GetMapping("/{quoteId}")
    public ResponseEntity<ResponseObject<QuoteReviewResponse>> detail(@CurrentUserUUID UUID staffId,
                                                                       @PathVariable UUID quoteId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.detail(staffId, quoteId)));
    }

    @PostMapping("/{quoteId}/packages/{packageId}/address-review")
    public ResponseEntity<ResponseObject<QuoteReviewResponse>> reviewAddress(@CurrentUserUUID UUID staffId,
                                                                            @PathVariable UUID quoteId,
                                                                            @PathVariable UUID packageId,
                                                                            @Valid @RequestBody QuoteAddressReviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.reviewAddress(staffId, quoteId, packageId, request)));
    }

    @PostMapping("/{quoteId}/review")
    public ResponseEntity<ResponseObject<QuoteReviewResponse>> decide(@CurrentUserUUID UUID staffId,
                                                                     @PathVariable UUID quoteId,
                                                                     @Valid @RequestBody QuoteDecisionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.decide(staffId, quoteId, request)));
    }
}
