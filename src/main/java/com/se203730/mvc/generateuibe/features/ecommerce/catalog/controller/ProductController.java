
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response.ProductPageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

        @Parameter(description = "name_asc | name_desc | newest | oldest")
        @RequestParam(defaultValue = "name_asc")
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
}
