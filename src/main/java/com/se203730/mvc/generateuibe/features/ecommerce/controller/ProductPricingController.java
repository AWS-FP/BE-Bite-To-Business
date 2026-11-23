
package com.se203730.mvc.generateuibe.features.ecommerce.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductPricingResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductPricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Catalog - Products")
public class ProductPricingController {

    private final ProductPricingService pricingService;

    @GetMapping("/pricing")
    @Operation(summary = "Get pricing and available stock for up to 100 products in one call")
    public ResponseEntity<ResponseObject<List<ProductPricingResponse>>> getPricingBatch(
        @RequestParam
        List<UUID> ids
    ) {
        return ResponseEntity.ok(
            new ResponseObject<>(200, "Success", pricingService.getPricingBatch(ids))
        );
    }

    @GetMapping("/{id}/pricing")
    @Operation(summary = "Get current sale, B2B price tiers and available stock")
    public ResponseEntity<ResponseObject<ProductPricingResponse>> getPricing(
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
            new ResponseObject<>(200, "Success", pricingService.getPricing(id))
        );
    }
}
