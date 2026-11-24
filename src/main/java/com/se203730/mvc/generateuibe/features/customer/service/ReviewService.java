package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.response.ReviewPageResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.ReviewResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.ReviewSummaryResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.request.UpdateReviewRequest;
import com.se203730.mvc.generateuibe.features.customer.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviews;
    private final ReviewImageStorage storage;
    private final AccountAccessService accounts;

    @Transactional(readOnly = true)
    /** Rating summaries for up to 100 products in one query (catalog cards). */
    public List<ReviewSummaryResponse> summaries(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) return List.of();
        List<UUID> ids = productIds.stream().distinct().toList();
        if (ids.size() > 100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return reviews.summaries(ids);
    }

    public ReviewPageResponse list(UUID productId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (!reviews.isPublished(productId)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return reviews.list(productId, page, size);
    }

    @Transactional(readOnly = true)
    public com.se203730.mvc.generateuibe.features.customer.dto.response.CustomerReviewsPageResponse
    listMine(UUID customerId, int page, int size) {
        requireCustomer(customerId);
        if (page < 0 || size < 1 || size > 50) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return reviews.listByCustomer(customerId, page, size);
    }

    @Transactional
    public ReviewResponse create(UUID customerId, UUID productId, BigDecimal rating,
                                 String content, List<MultipartFile> images) {
        requireCustomer(customerId);
        if (!reviews.isPublished(productId)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        validateRating(rating);
        String cleaned = normalizeContent(content);
        List<MultipartFile> incoming = images == null ? List.of() : images;
        if (incoming.size() > ReviewImageStorage.MAX_IMAGES) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (reviews.hasActiveReview(customerId, productId)) throw new BusinessException(ErrorCode.CONFLICT);
        if (!incoming.isEmpty()) storage.validate(incoming);

        // Upload first; a DB rollback cleans up newly uploaded Cloudinary assets.
        var uploaded = incoming.isEmpty() ? List.<ReviewImageStorage.UploadedImage>of()
            : storage.uploadBatch(incoming);
        deleteUploadedOnRollback(uploaded);
        UUID reviewId = reviews.insert(customerId, productId, rating, cleaned);
        int order = 0;
        for (var image : uploaded) reviews.insertImage(reviewId, image.url(), image.publicId(), order++);
        return requireReview(reviewId);
    }

    @Transactional
    public ReviewResponse update(UUID customerId, UUID reviewId, UpdateReviewRequest body) {
        requireOwner(customerId, reviewId);
        validateRating(body.rating());
        if (reviews.update(reviewId, customerId, body.rating(), normalizeContent(body.content())) != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return requireReview(reviewId);
    }

    @Transactional
    public ReviewResponse addImages(UUID customerId, UUID reviewId, List<MultipartFile> images) {
        requireOwner(customerId, reviewId);
        // Lock review row to prevent concurrent requests from exceeding the 5-image limit.
        reviews.lockOwnedReview(reviewId, customerId);
        storage.validate(images);
        if (reviews.countImages(reviewId) + images.size() > ReviewImageStorage.MAX_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var uploaded = storage.uploadBatch(images);
        deleteUploadedOnRollback(uploaded);
        int order = reviews.nextSortOrder(reviewId);
        for (var image : uploaded) reviews.insertImage(reviewId, image.url(), image.publicId(), order++);
        return requireReview(reviewId);
    }

    @Transactional
    public ReviewResponse removeImage(UUID customerId, UUID reviewId, UUID imageId) {
        requireOwner(customerId, reviewId);
        reviews.lockOwnedReview(reviewId, customerId);
        var image = reviews.findImage(reviewId, imageId);
        if (image == null || reviews.deleteImage(reviewId, imageId) != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String publicId = image.publicId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { storage.deleteBestEffort(publicId); }
            });
        }
        return requireReview(reviewId);
    }

    @Transactional
    public void delete(UUID customerId, UUID reviewId) {
        requireOwner(customerId, reviewId);
        if (reviews.softDelete(reviewId, customerId) != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // Soft delete: the image records remain for audit/moderation, but public GET excludes this review.
    }

    private void requireCustomer(UUID customerId) {
        if (accounts.requireActiveUser(customerId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void requireOwner(UUID customerId, UUID reviewId) {
        requireCustomer(customerId);
        if (!reviews.isOwner(reviewId, customerId)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private ReviewResponse requireReview(UUID reviewId) {
        ReviewResponse result = reviews.findReview(reviewId);
        if (result == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return result;
    }

    private void validateRating(BigDecimal rating) {
        if (rating == null || rating.compareTo(new BigDecimal("0.5")) < 0
            || rating.compareTo(new BigDecimal("5")) > 0
            || rating.remainder(new BigDecimal("0.5")).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private String normalizeContent(String content) {
        if (content == null) return null;
        String value = content.strip();
        if (value.length() > 2000) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return value.isEmpty() ? null : value;
    }

    private void deleteUploadedOnRollback(List<ReviewImageStorage.UploadedImage> uploaded) {
        if (uploaded.isEmpty()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) storage.cleanup(uploaded);
            }
        });
    }
}
