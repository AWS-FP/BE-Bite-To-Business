package com.se203730.mvc.generateuibe.features.ecommerce.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.UpdateProductVatRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductVatResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductVatService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ecommerce/products")
@PreAuthorize("hasRole('ECOMMERCE_EXECUTIVE')")
public class ProductVatController {
    @Autowired
    private ProductVatService service;

    @GetMapping("/{productId}/vat-rate")
    public ResponseEntity<ResponseObject<ProductVatResponse>> get(
            @CurrentUserUUID
            UUID staffId,
            @PathVariable
            UUID productId) {
        ProductVatResponse product = service.get(staffId, productId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", product));
    }

    @PutMapping("/{productId}/vat-rate")
    public ResponseEntity<ResponseObject<ProductVatResponse>> update(
            @CurrentUserUUID
            UUID staffId,
            @PathVariable
            UUID productId,
            @Valid
            @RequestBody
            UpdateProductVatRequest request) {
        ProductVatResponse product = service.update(staffId, productId, request.vatRate());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Product VAT updated.", product));
    }
}
