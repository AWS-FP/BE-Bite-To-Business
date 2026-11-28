package com.se203730.mvc.generateuibe.features.ecommerce.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutiveComboRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ExecutiveComboService;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ExecutivePromotionImageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @Validated
@RequestMapping("/api/ecommerce/combos")
@PreAuthorize("hasRole('ECOMMERCE_EXECUTIVE')")
@Tag(name="E-commerce - Combos")
public class ExecutiveComboController {
    private final ExecutiveComboService service;
    private final ExecutivePromotionImageService images;
    public ExecutiveComboController(ExecutiveComboService service,ExecutivePromotionImageService images) {
        this.service=service; this.images=images;
    }
    private static <T> ResponseEntity<ResponseObject<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ResponseObject<>(200,"Success",data));
    }
    @GetMapping
    public ResponseEntity<ResponseObject<EcommercePageResponse<Map<String,Object>>>> list(
        @RequestParam(required=false) String q,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ok(service.list(q,page,size));
    }
    @GetMapping("/ingredients")
    public ResponseEntity<ResponseObject<EcommercePageResponse<Map<String,Object>>>> ingredients(
        @RequestParam(required=false) String q,@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="12") @Min(1) @Max(100) int size) { return ok(service.ingredients(q,page,size)); }
    @GetMapping("/categories")
    public ResponseEntity<ResponseObject<List<Map<String,Object>>>> categories() { return ok(service.categories()); }
    @GetMapping("/{id}")
    public ResponseEntity<ResponseObject<Map<String,Object>>> detail(@PathVariable UUID id) { return ok(service.detail(id)); }
    @PostMapping
    public ResponseEntity<ResponseObject<Map<String,Object>>> create(@Valid @RequestBody ExecutiveComboRequest r) { return ok(service.create(r)); }
    @PutMapping("/{id}")
    public ResponseEntity<ResponseObject<Map<String,Object>>> update(@PathVariable UUID id,@Valid @RequestBody ExecutiveComboRequest r) { return ok(service.update(id,r)); }
    @PatchMapping("/{id}/status")
    public ResponseEntity<ResponseObject<Map<String,Object>>> status(@PathVariable UUID id,@RequestParam String status) { return ok(service.status(id,status)); }
    @PostMapping(value="/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseObject<ExecutivePromotionImageService.UploadedImage>> image(@RequestPart("file") MultipartFile file) { return ok(images.uploadCombo(file)); }
}
