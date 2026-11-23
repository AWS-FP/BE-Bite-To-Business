
package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.response.CustomerBrandDirectoryItemResponse;
import com.se203730.mvc.generateuibe.features.customer.service.CustomerBrandDiscoveryService;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.BrandResponse;
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

/** Public Customer read endpoints under the existing catalog route. */
@RestController
@RequestMapping("/api/catalog/brands")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Customer - Brand Discovery")
public class CustomerBrandDiscoveryController {

    private final CustomerBrandDiscoveryService service;

    @GetMapping("/directory")
    @Operation(summary = "Brand directory with published product counts and category IDs")
    public ResponseEntity<ResponseObject<List<CustomerBrandDirectoryItemResponse>>> directory() {
        return ResponseEntity.ok(
            new ResponseObject<>(200, "Success", service.directory())
        );
    }

    @GetMapping("/{brandId}/related")
    @Operation(summary = "Other brands with published products in matching categories")
    public ResponseEntity<ResponseObject<List<BrandResponse>>> related(
        @PathVariable UUID brandId,
        @RequestParam(defaultValue = "12") int limit
    ) {
        return ResponseEntity.ok(
            new ResponseObject<>(200, "Success", service.related(brandId, limit))
        );
    }
}
