
package com.se203730.mvc.generateuibe.features.ecommerce.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductDetailResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductPageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.CatalogSuggestion;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductSummaryResponse;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Catalog - Products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(
        summary = "List published products with search, filters and sorting"
    )
    public ResponseEntity<ResponseObject<ProductPageResponse>> getProducts(
        @Parameter(description = "Search by Vietnamese/English name or SKU")
        @RequestParam(required = false)
        String q,

        @RequestParam(required = false)
        UUID categoryId,

        @RequestParam(required = false)
        UUID brandId,

        @Parameter(description = "relevance (search default) | name_asc | name_desc | newest | oldest")
        @RequestParam(required = false)
        String sort,

        @RequestParam(defaultValue = "0")
        int page,

        @RequestParam(defaultValue = "20")
        int size
    ) {
        ProductPageResponse data = productService.getAll(
            q,
            categoryId,
            brandId,
            sort,
            page,
            size
        );

        return ResponseEntity.ok(
            new ResponseObject<>(200, "Success", data)
        );
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ResponseObject<List<CatalogSuggestion>>> suggestions(@RequestParam String q) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", productService.suggestions(q)));
    }

    @GetMapping("/{id}/related")
    public ResponseEntity<ResponseObject<List<ProductSummaryResponse>>> related(@PathVariable UUID id) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", productService.related(id)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get details of a published product by ID")
    public ResponseEntity<ResponseObject<ProductDetailResponse>> getById(
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
            new ResponseObject<>(200, "Success", productService.getById(id))
        );
    }
}
