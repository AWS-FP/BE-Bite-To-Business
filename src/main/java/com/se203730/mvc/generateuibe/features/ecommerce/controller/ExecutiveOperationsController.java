package com.se203730.mvc.generateuibe.features.ecommerce.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ExecutiveOperationsService;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.*;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ExecutiveDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequiredArgsConstructor @Validated
@RequestMapping("/api/ecommerce")
@PreAuthorize("hasRole('ECOMMERCE_EXECUTIVE')")
@Tag(name="E-commerce - Dashboard, Quotes and Reviews")
public class ExecutiveOperationsController {
    private final ExecutiveOperationsService service;
    private final ExecutiveDashboardService dashboard;
    private static <T> ResponseEntity<ResponseObject<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new ResponseObject<>(200,"Success",data));
    }
    @GetMapping("/dashboard/metrics")
    public ResponseEntity<ResponseObject<Map<String,Object>>> metrics(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(dashboard.metrics(from,to));
    }
    @PreAuthorize("hasRole('LOGISTICS_STAFF')")
    @GetMapping("/quotes")
    public ResponseEntity<ResponseObject<EcommercePageResponse<Map<String,Object>>>> quotes(
        @RequestParam(required=false) String status,@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ok(service.quotes(status,page,size));
    }
    @PreAuthorize("hasRole('LOGISTICS_STAFF')")
    @GetMapping("/quotes/{id}")
    public ResponseEntity<ResponseObject<Map<String,Object>>> quote(@PathVariable UUID id) {
        return ok(service.quote(id));
    }
    @PreAuthorize("hasRole('LOGISTICS_STAFF')")
    @GetMapping("/quotes/{id}/readiness")
    public ResponseEntity<ResponseObject<Map<String,Object>>> quoteReadiness(@PathVariable UUID id) {
        return ok(service.quoteReadiness(id));
    }
    @PreAuthorize("hasRole('LOGISTICS_STAFF')")
    @PostMapping("/quotes/{id}/decision")
    public ResponseEntity<ResponseObject<Map<String,Object>>> decideQuote(@CurrentUserUUID UUID staff,
                                                                          @PathVariable UUID id,@Valid @RequestBody ExecutiveQuoteDecisionRequest r) {
        return ok(service.decideQuote(id,staff,r));
    }
    @GetMapping("/reviews")
    public ResponseEntity<ResponseObject<EcommercePageResponse<Map<String,Object>>>> reviews(
        @RequestParam(required=false) String status,
        @RequestParam(required=false) @Min(1) @Max(5) Integer stars,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ok(service.reviews(status,stars,page,size));
    }
    @PatchMapping("/reviews/{id}/moderation")
    public ResponseEntity<ResponseObject<Map<String,Object>>> moderateReview(@CurrentUserUUID UUID staff,
                                                                             @PathVariable UUID id,@Valid @RequestBody ExecutiveReviewDecisionRequest r) {
        return ok(service.moderateReview(id,staff,r));
    }
    // Restricted by controller-level @PreAuthorize to ECOMMERCE_EXECUTIVE.
    // Permanent removal is distinct from the reversible HIDE moderation action.
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<ResponseObject<Map<String,Object>>> deleteReview(@CurrentUserUUID UUID staff,
                                                                            @PathVariable UUID id) {
        service.deleteReview(id, staff);
        return ok(Map.of("reviewId", id, "deleted", true));
    }
}
