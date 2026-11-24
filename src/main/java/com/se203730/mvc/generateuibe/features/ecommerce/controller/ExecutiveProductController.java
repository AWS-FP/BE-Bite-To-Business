package com.se203730.mvc.generateuibe.features.ecommerce.controller;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.*;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ExecutiveProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/ecommerce/products")
@PreAuthorize("hasRole('ECOMMERCE_EXECUTIVE')")
@RequiredArgsConstructor @Validated
@Tag(name="E-commerce - Product Management")
public class ExecutiveProductController {
    private final ExecutiveProductService service;
    private static <T> ResponseEntity<ResponseObject<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new ResponseObject<>(200,"Success",data));
    }
    @GetMapping
    public ResponseEntity<ResponseObject<EcommercePageResponse<Map<String,Object>>>> list(
        @RequestParam(required=false) String q, @RequestParam(required=false) String status,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ok(service.list(q,status,page,size));
    }
    @PatchMapping("/{id}/status")
    public ResponseEntity<ResponseObject<Map<String,Object>>> status(@PathVariable UUID id,
        @Valid @RequestBody ExecutiveProductStatusRequest request) {
        return ok(service.updateStatus(id,request));
    }
    @GetMapping("/{id}/manage-pricing")
    public ResponseEntity<ResponseObject<Map<String,Object>>> pricing(@PathVariable UUID id) {
        return ok(service.pricing(id));
    }
    @PutMapping("/{id}/pricing")
    public ResponseEntity<ResponseObject<Map<String,Object>>> pricing(@PathVariable UUID id,
        @Valid @RequestBody ExecutivePricingRequest request) {
        return ok(service.putPricing(id,request));
    }
}
