package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteDraftResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuotePackageResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteReviewResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackage;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteNotification;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteNotificationRepository;
import com.se203730.mvc.generateuibe.features.customer.service.QuoteWorkflowService;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.QuoteAddressReviewRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.QuoteDecisionRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.entity.QuoteAddressReview;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.QuoteAddressReviewRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.QuoteAddressEmailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Service
public class QuoteReviewService {
    @Autowired
    private AccountAccessService accounts;
    @Autowired
    private QuoteRepository quotes;
    @Autowired
    private QuoteItemRepository items;
    @Autowired
    private QuotePackageRepository packages;
    @Autowired
    private QuotePackageItemRepository allocations;
    @Autowired
    private QuoteAddressReviewRepository addressReviews;
    @Autowired
    private QuoteNotificationRepository notifications;
    @Autowired
    private DatabaseClock clock;
    @Autowired
    private QuoteWorkflowService workflow;
    @Autowired
    private QuoteAddressEmailRepository emailOutbox;

    @Transactional(readOnly = true)
    public Page<QuoteReviewResponse> listPending(UUID staffId, int page, int size) {
        requireEcommerce(staffId);
        return quotes.findByStatusOrderBySubmittedAtAsc("PENDING_APPROVAL", PageRequest.of(page, size))
                .map(this::response);
    }

    @Transactional(readOnly = true)
    public QuoteReviewResponse detail(UUID staffId, UUID quoteId) {
        requireEcommerce(staffId);
        Quote quote = quotes.findById(quoteId)
                .filter(value -> !"DRAFT".equals(value.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return response(quote);
    }

    @Transactional
    public QuoteReviewResponse reviewAddress(UUID staffId, UUID quoteId, UUID packageId,
                                             QuoteAddressReviewRequest request) {
        Quote quote = pendingQuote(staffId, quoteId, request.revision());
        QuotePackage parcel = packages.findByIdAndQuoteIdAndCancelledAtIsNull(packageId, quoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (request.valid() && "VALID".equals(parcel.getAddressStatus())) return response(quote);
        String reason = request.reason() == null ? null : request.reason().strip();
        if (!request.valid() && (reason == null || reason.isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Instant now = clock.now();
        String status = request.valid() ? "VALID" : "NEEDS_UPDATE";
        QuoteAddressReview review = new QuoteAddressReview();
        review.setId(UUID.randomUUID());
        review.setQuoteId(quoteId);
        review.setQuotePackageId(packageId);
        review.setQuoteRevision(quote.getRevision());
        review.setReviewedBy(staffId);
        review.setResult(status);
        review.setReason(reason);
        review.setReviewedAt(now);
        addressReviews.save(review);
        parcel.setAddressStatus(status);
        parcel.setUpdatedAt(now);
        packages.saveAndFlush(parcel);
        if (!request.valid()) {
            emailOutbox.enqueue(quoteId, packageId, quote.getRevision(), quote.getContactEmail(),
                    quote.getQuoteNumber(), reason);
            returnToDraft(quote, reason, now);
        }
        return response(quote);
    }

    @Transactional
    public QuoteReviewResponse decide(UUID staffId, UUID quoteId, QuoteDecisionRequest request) {
        Quote quote = pendingQuote(staffId, quoteId, request.revision());
        Instant now = clock.now();
        if ("RETURN_FOR_CHANGES".equals(request.decision())) {
            String note = request.note() == null ? null : request.note().strip();
            if (note == null || note.isEmpty()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            returnToDraft(quote, note, now);
            return response(quote);
        }
        List<QuotePackage> parcels = packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(quoteId);
        if (parcels.stream().anyMatch(parcel -> !"VALID".equals(parcel.getAddressStatus()))) {
            throw new BusinessException(ErrorCode.QUOTE_ADDRESS_UNVERIFIED);
        }
        List<QuoteItem> lines = items.findAllByQuoteIdOrderByCreatedAtAsc(quoteId);
        if (lines.stream().anyMatch(item -> !item.isVatRateConfigured())) {
            throw new BusinessException(ErrorCode.QUOTE_PRODUCT_VAT_MISSING);
        }
        workflow.validateQuote(quote, lines, parcels, now);
        // Final approval is not enabled until the order and reservation flow is implemented.
        throw new BusinessException(ErrorCode.QUOTE_APPROVAL_NOT_READY);
    }

    private Quote pendingQuote(UUID staffId, UUID quoteId, int expectedRevision) {
        requireEcommerce(staffId);
        Quote quote = quotes.findForUpdate(quoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"PENDING_APPROVAL".equals(quote.getStatus())) throw new BusinessException(ErrorCode.CONFLICT);
        if (quote.getRevision() != expectedRevision) throw new BusinessException(ErrorCode.QUOTE_REVISION_CONFLICT);
        return quote;
    }

    private void returnToDraft(Quote quote, String reason, Instant now) {
        quote.setStatus("DRAFT");
        quote.setRevision(quote.getRevision() + 1);
        quote.setReviewNote(reason);
        quote.setUpdatedAt(now);
        quotes.saveAndFlush(quote);

        QuoteNotification notification = new QuoteNotification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientId(quote.getCustomerId());
        notification.setNotificationType("QUOTE");
        notification.setTitleVi("Báo giá cần cập nhật");
        notification.setTitleEn("Quote requires changes");
        notification.setContentVi("Vui lòng xem ghi chú và cập nhật báo giá " + quote.getQuoteNumber() + ".");
        notification.setContentEn("Please review the notes and update quote " + quote.getQuoteNumber() + ".");
        notification.setCreatedAt(now);
        notifications.save(notification);
    }

    private QuoteReviewResponse response(Quote quote) {
        List<QuotePackageResponse> result = packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(quote.getId())
                .stream().map(this::packageResponse).toList();
        return new QuoteReviewResponse(quote.getCustomerId(), quote.getContactName(), quote.getContactPhone(),
                quote.getContactEmail(), quote.getCompanyName(),
                QuoteDraftResponse.from(quote, items.findAllByQuoteIdOrderByCreatedAtAsc(quote.getId())), result);
    }

    private QuotePackageResponse packageResponse(QuotePackage parcel) {
        return QuotePackageResponse.from(parcel, allocations.findAllByQuotePackageId(parcel.getId()));
    }

    private void requireEcommerce(UUID staffId) {
        if (accounts.requireActiveUser(staffId).getRole() != UserRole.ECOMMERCE_EXECUTIVE) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
