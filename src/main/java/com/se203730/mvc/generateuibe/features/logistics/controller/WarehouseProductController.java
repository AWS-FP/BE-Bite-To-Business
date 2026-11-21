package com.se203730.mvc.generateuibe.features.logistics.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.logistics.dto.CreateWarehouseProductRequest;
import com.se203730.mvc.generateuibe.features.logistics.service.WarehouseProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/api/logistics/products")
@PreAuthorize("hasRole('LOGISTICS_STAFF')")
@RequiredArgsConstructor
public class WarehouseProductController {
    private final WarehouseProductService products;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseObject<WarehouseProductService.CreatedProduct>> create(
            @CurrentUserUUID
            UUID userId,
            @Valid
            @RequestPart("data")
            CreateWarehouseProductRequest request,
            @RequestPart(value = "image", required = false)
            MultipartFile image) {
        return ResponseEntity.status(201).body(new ResponseObject<>(201, "Product created", products.create(userId, request, image)));
    }
}
