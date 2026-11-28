package com.se203730.mvc.generateuibe.features.ecommerce.controller;
import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.*;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ExecutiveMarketingService;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ExecutivePromotionImageService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @Validated
@PreAuthorize("hasRole('ECOMMERCE_EXECUTIVE')")
@RequestMapping("/api/ecommerce")
@Tag(name="E-commerce - Coupons and Promotions")
public class ExecutiveMarketingController {
    private final ExecutiveMarketingService service;
    private final ExecutivePromotionImageService imageService;
    private static <T> ResponseEntity<ResponseObject<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new ResponseObject<>(200,"Success",data));
    }
    @PostMapping(value = "/promotions/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseObject<ExecutivePromotionImageService.UploadedImage>> uploadPromotionImage(
        @RequestPart("file") MultipartFile file) {
        return ok(imageService.upload(file));
    }
    @GetMapping("/coupons")
    public ResponseEntity<ResponseObject<EcommercePageResponse<Map<String,Object>>>> coupons(
        @RequestParam(name="q", required=false) String q,
        @RequestParam(name="status", required=false) String status,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ok(service.coupons(q,status,page,size));
    }
    @GetMapping("/coupons/{id}")
    public ResponseEntity<ResponseObject<Map<String,Object>>> coupon(@PathVariable UUID id) {
        return ok(service.coupon(id));
    }
    @PostMapping("/coupons")
    public ResponseEntity<ResponseObject<Map<String,Object>>> createCoupon(
        @CurrentUserUUID UUID staff, @Valid @RequestBody ExecutiveCouponRequest r) {
        return ok(service.createCoupon(staff,r));
    }
    @PutMapping("/coupons/{id}")
    public ResponseEntity<ResponseObject<Map<String,Object>>> updateCoupon(
        @PathVariable UUID id,@Valid @RequestBody ExecutiveCouponRequest r) {
        return ok(service.updateCoupon(id,r));
    }
    @PatchMapping("/coupons/{id}/active")
    public ResponseEntity<ResponseObject<Map<String,Object>>> activeCoupon(
        @PathVariable UUID id,@RequestParam boolean active) {
        return ok(service.setCouponActive(id,active));
    }
    @GetMapping("/promotions")
    public ResponseEntity<ResponseObject<EcommercePageResponse<Map<String,Object>>>> promotions(
        @RequestParam(required=false) String status,
        @RequestParam(required=false) String q,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ok(service.promotions(status,q,page,size));
    }
    @GetMapping("/promotions/{id}")
    public ResponseEntity<ResponseObject<Map<String,Object>>> promotion(@PathVariable UUID id) {
        return ok(service.promotion(id));
    }
    @PostMapping("/promotions")
    public ResponseEntity<ResponseObject<Map<String,Object>>> createPromotion(
        @CurrentUserUUID UUID staff,@Valid @RequestBody ExecutivePromotionRequest r) {
        return ok(service.createPromotion(staff,r));
    }
    @PutMapping("/promotions/{id}")
    public ResponseEntity<ResponseObject<Map<String,Object>>> updatePromotion(
        @PathVariable UUID id,@Valid @RequestBody ExecutivePromotionRequest r) {
        return ok(service.updatePromotion(id,r));
    }
    @DeleteMapping("/promotions/{id}")
    public ResponseEntity<ResponseObject<Map<String,Object>>> deletePromotion(@PathVariable UUID id) {
        service.deletePromotion(id);
        return ok(Map.of("deleted",true,"id",id));
    }
    @GetMapping("/promotions/{id}/product-ids")
    public ResponseEntity<ResponseObject<List<UUID>>> promotionProductIds(@PathVariable UUID id) {
        return ok(service.promotionProductIds(id));
    }
    @GetMapping("/promotions/{id}/products")
    public ResponseEntity<ResponseObject<EcommercePageResponse<Map<String,Object>>>> promotionProductChoices(
        @PathVariable UUID id, @RequestParam(required=false) String q,
        @RequestParam(required=false) UUID brandId,
        @RequestParam(required=false) UUID categoryId,
        @RequestParam(defaultValue="false") boolean inStockOnly,
        @RequestParam(defaultValue="false") boolean discountOnly,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ok(service.promotionProductChoices(id,
            new ExecutivePromotionProductFilterRequest(q,brandId,categoryId,inStockOnly,discountOnly),page,size));
    }
    @PostMapping("/promotions/{id}/products/resolve")
    public ResponseEntity<ResponseObject<List<Map<String,Object>>>> resolvePromotionProducts(
        @PathVariable UUID id,@Valid @RequestBody ExecutivePromotionProductsRequest r) {
        return ok(service.promotionProductDetails(id,r.productIds()));
    }
    @PostMapping("/promotions/{id}/products/matching")
    public ResponseEntity<ResponseObject<List<UUID>>> matchingPromotionProducts(
        @PathVariable UUID id,@RequestBody ExecutivePromotionProductFilterRequest r) {
        return ok(service.promotionMatchingProductIds(id,r));
    }
    @PutMapping("/promotions/{id}/products")
    public ResponseEntity<ResponseObject<Map<String,Object>>> replacePromotionProducts(
        @PathVariable UUID id,@Valid @RequestBody ExecutivePromotionProductsRequest r) {
        return ok(service.replacePromotionProducts(id,r));
    }
    @PostMapping("/promotions/{id}/publish")
    public ResponseEntity<ResponseObject<Map<String,Object>>> publish(@PathVariable UUID id) {
        return ok(service.setPromotionStatus(id,"PUBLISHED"));
    }
    @PostMapping("/promotions/{id}/disable")
    public ResponseEntity<ResponseObject<Map<String,Object>>> disable(@PathVariable UUID id) {
        return ok(service.setPromotionStatus(id,"DISABLED"));
    }
    @PostMapping("/promotions/{id}/restore")
    public ResponseEntity<ResponseObject<Map<String,Object>>> restore(@PathVariable UUID id) {
        return ok(service.setPromotionStatus(id,"DRAFT"));
    }
    @PostMapping("/promotions/{id}/archive")
    public ResponseEntity<ResponseObject<Map<String,Object>>> archive(@PathVariable UUID id) {
        return ok(service.setPromotionStatus(id,"ARCHIVED"));
    }
}
