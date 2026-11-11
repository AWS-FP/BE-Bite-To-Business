package com.se203730.mvc.generateuibe.features.ecommerce.catalog.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response.CategoryResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.service.CategoryService;
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
@RequestMapping("/api/catalog/categories")
@RequiredArgsConstructor
@SecurityRequirements // Swagger: endpoint công khai, không yêu cầu Bearer JWT.
@Tag(name = "Catalog - Categories")
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get active category tree")
    public ResponseEntity<ResponseObject<List<CategoryResponse>>> getTree() {
        return success(categoryService.getTree());
    }

    @GetMapping("/parents")
    @Operation(summary = "Get active parent categories")
    public ResponseEntity<ResponseObject<List<CategoryResponse>>> getParents() {
        return success(categoryService.getParents());
    }

    @GetMapping("/{parentId}/children")
    @Operation(summary = "Get children of an active category")
    public ResponseEntity<ResponseObject<List<CategoryResponse>>> getChildren(
            @PathVariable UUID parentId) {
        return success(categoryService.getChildren(parentId));
    }

    private <T> ResponseEntity<ResponseObject<T>> success(T data) {
        return ResponseEntity.ok().body(new ResponseObject<>(200, "Success", data));
    }
}
