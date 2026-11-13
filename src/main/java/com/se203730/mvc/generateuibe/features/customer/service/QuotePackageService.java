package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.dto.request.QuotePackageRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuotePackageResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackage;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackageItem;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteCatalogRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class QuotePackageService {
    @Autowired
    private QuoteDraftService drafts;
    @Autowired
    private QuoteRepository quotes;
    @Autowired
    private QuoteItemRepository items;
    @Autowired
    private QuoteCatalogRepository catalog;
    @Autowired
    private QuotePackageRepository packages;
    @Autowired
    private QuotePackageItemRepository allocations;
    @Autowired
    private DatabaseClock clock;
    @Autowired
    private QuoteCouponService couponService;

    @Transactional(readOnly = true)
    public List<QuotePackageResponse> list(UUID customerId, UUID quoteId) {
        drafts.get(customerId, quoteId);
        return packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(quoteId).stream()
                .map(this::response).toList();
    }

    @Transactional
    public QuotePackageResponse save(UUID customerId, UUID quoteId, UUID packageId, QuotePackageRequest request,
                                     DeliveryEstimateService.Estimate estimate) {
        Quote quote = drafts.editableQuote(customerId, quoteId);
        QuotePackage parcel;
        Instant now = clock.now();
        if (packageId == null) {
            parcel = new QuotePackage();
            parcel.setId(UUID.randomUUID());
            parcel.setQuoteId(quoteId);
            parcel.setCreatedBy(customerId);
            parcel.setCreatedAt(now);
        } else {
            parcel = packages.findByIdAndQuoteIdAndCancelledAtIsNull(packageId, quoteId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        }

        Set<UUID> seen = new HashSet<>();
        for (QuotePackageRequest.Allocation allocation : request.items()) {
            if (!seen.add(allocation.quoteItemId())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            QuoteItem item = items.findByIdAndQuoteId(allocation.quoteItemId(), quoteId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            var product = catalog.findPrice(item.getProductId(), item.getQuantity())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (allocation.quantity().remainder(product.getQuantityStep()).signum() != 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            BigDecimal allocated = allocations.allocatedElsewhere(item.getId(), parcel.getId());
            if (allocated.add(allocation.quantity()).compareTo(item.getQuantity()) > 0) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
        }

        if (estimate.distanceMetres() > Integer.MAX_VALUE) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        parcel.setPackageName(request.name().trim());
        parcel.setRecipientName(request.recipientName().trim());
        parcel.setRecipientPhone(request.recipientPhone().trim());
        parcel.setDeliveryDate(request.deliveryDate());
        parcel.setDeliveryTimeSlot(request.deliveryTimeSlot().trim());
        parcel.setDeliveryAddress(request.address().trim());
        parcel.setDeliveryNote(request.note() == null ? null : request.note().trim());
        parcel.setCity(request.city());
        parcel.setLatitude(request.latitude());
        parcel.setLongitude(request.longitude());
        parcel.setDistanceMetres((int) Math.round(estimate.distanceMetres()));
        parcel.setShippingFee(BigDecimal.valueOf(estimate.fee()));
        // A routable coordinate alone does not verify the typed address or service area.
        parcel.setAddressStatus("UNVERIFIED");
        parcel.setUpdatedAt(now);
        packages.saveAndFlush(parcel);
        allocations.deleteAllByQuotePackageId(parcel.getId());
        allocations.flush();
        for (QuotePackageRequest.Allocation allocation : request.items()) {
            QuotePackageItem row = new QuotePackageItem();
            row.setId(UUID.randomUUID());
            row.setQuoteId(quoteId);
            row.setQuotePackageId(parcel.getId());
            row.setQuoteItemId(allocation.quoteItemId());
            row.setQuantity(allocation.quantity());
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            allocations.save(row);
        }
        allocations.flush();
        updateShipping(quote, now);
        return response(parcel);
    }

    @Transactional
    public void remove(UUID customerId, UUID quoteId, UUID packageId) {
        Quote quote = drafts.editableQuote(customerId, quoteId);
        QuotePackage parcel = packages.findByIdAndQuoteIdAndCancelledAtIsNull(packageId, quoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Instant now = clock.now();
        parcel.setCancelledAt(now);
        parcel.setUpdatedAt(now);
        packages.saveAndFlush(parcel);
        updateShipping(quote, now);
    }

    private void updateShipping(Quote quote, Instant now) {
        BigDecimal fee = packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(quote.getId()).stream()
                .map(QuotePackage::getShippingFee).filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        quote.setShippingCouponDiscountAmount(BigDecimal.ZERO);
        quote.setShippingFee(fee);
        couponService.apply(quote, now);
        quote.setRevision(quote.getRevision() + 1);
        quote.setUpdatedAt(now);
        quotes.saveAndFlush(quote);
    }

    private QuotePackageResponse response(QuotePackage parcel) {
        return QuotePackageResponse.from(parcel, allocations.findAllByQuotePackageId(parcel.getId()));
    }
}
