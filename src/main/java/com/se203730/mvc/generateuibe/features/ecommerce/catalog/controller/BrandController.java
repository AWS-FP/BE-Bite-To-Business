
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response.BrandProductResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response.BrandResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/brands")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Catalog - Brands")
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    @Operation(summary = "Get all brands sorted by name")
    public ResponseEntity<ResponseObject<List<BrandResponse>>> getAll() {
        return success(brandService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a brand by ID")
    public ResponseEntity<ResponseObject<BrandResponse>> getById(
        @PathVariable UUID id
    ) {
        return success(brandService.getById(id));
    }

    @GetMapping("/{id}/products")
    @Operation(summary = "Get all published products of a brand")
    public ResponseEntity<ResponseObject<List<BrandProductResponse>>> getProducts(
        @PathVariable UUID id
    ) {
        return success(brandService.getProducts(id));
    }

    private <T> ResponseEntity<ResponseObject<T>> success(T data) {
        return ResponseEntity.ok(
            new ResponseObject<>(200, "Success", data)
        );
    }
}
