package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.response.ReviewResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.request.UpdateReviewRequest;
import com.se203730.mvc.generateuibe.features.customer.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer/reviews")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
@Tag(name = "Customer - Reviews")
public class CustomerReviewController {
    private final ReviewService service;

    @GetMapping
    @Operation(summary = "List all reviews created by the authenticated customer (paginated)")
    public ResponseEntity<ResponseObject<
            com.se203730.mvc.generateuibe.features.customer.dto.response.CustomerReviewsPageResponse>> listMine(
            @CurrentUserUUID UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.listMine(customerId, page, size)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a review: productId, rating, optional text and up to 5 photos")
    public ResponseEntity<ResponseObject<ReviewResponse>> create(
            @CurrentUserUUID UUID customerId,
            @RequestParam UUID productId,
            @RequestParam BigDecimal rating,
            @RequestParam(required = false) String content,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Review created.",
                        service.create(customerId, productId, rating, content, images)));
    }

    @PutMapping("/{reviewId}")
    @Operation(summary = "Update the rating/text of my review; keeps existing photos")
    public ResponseEntity<ResponseObject<ReviewResponse>> update(
            @CurrentUserUUID UUID customerId, @PathVariable UUID reviewId,
            @Valid @RequestBody UpdateReviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Review updated.", service.update(customerId, reviewId, request)));
    }

    @PostMapping(value = "/{reviewId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add photos to my review (maximum 5 photos in total)")
    public ResponseEntity<ResponseObject<ReviewResponse>> addImages(
            @CurrentUserUUID UUID customerId, @PathVariable UUID reviewId,
            @RequestPart("images") List<MultipartFile> images) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Review images added.",
                        service.addImages(customerId, reviewId, images)));
    }

    @DeleteMapping("/{reviewId}/images/{imageId}")
    @Operation(summary = "Remove one photo from my review")
    public ResponseEntity<ResponseObject<ReviewResponse>> removeImage(
            @CurrentUserUUID UUID customerId, @PathVariable UUID reviewId,
            @PathVariable UUID imageId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Review image removed.",
                        service.removeImage(customerId, reviewId, imageId)));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Soft-delete my review; hide it from public product details")
    public ResponseEntity<ResponseObject<Void>> delete(
            @CurrentUserUUID UUID customerId, @PathVariable UUID reviewId) {
        service.delete(customerId, reviewId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Review deleted.", null));
    }
}
