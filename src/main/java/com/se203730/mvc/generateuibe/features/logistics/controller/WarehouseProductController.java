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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.se203730.mvc.generateuibe.features.logistics.repository.WarehouseProductRepository;
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

    @GetMapping
    public ResponseEntity<ResponseObject<WarehouseProductRepository.EditPage>> editable(
            @CurrentUserUUID UUID userId, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Editable products", products.editable(userId, q, page, size)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ResponseObject<Map<String, Object>>> detail(
            @CurrentUserUUID UUID userId, @PathVariable UUID productId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Product", products.editDetail(userId, productId)));
    }

    @PutMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseObject<WarehouseProductService.CreatedProduct>> update(
            @CurrentUserUUID UUID userId, @PathVariable UUID productId,
            @RequestPart("data") CreateWarehouseProductRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Product updated", products.update(userId, productId, request, image)));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{productId}")
    public ResponseEntity<ResponseObject<Void>> disable(@CurrentUserUUID UUID userId,
            @org.springframework.web.bind.annotation.PathVariable UUID productId) {
        products.disable(userId,productId);
        return ResponseEntity.ok(new ResponseObject<>(200,"Product disabled",null));
    }
    // Danh sách nhà phân phối cho dropdown ở form tạo sản phẩm.
    @GetMapping("/distributors")
    public ResponseEntity<ResponseObject<List<WarehouseProductService.DistributorOption>>> distributors(
            @CurrentUserUUID
            UUID userId) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Distributors", products.distributors(userId)));
    }
}
