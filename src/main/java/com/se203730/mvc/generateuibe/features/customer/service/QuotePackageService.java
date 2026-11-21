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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
    @Autowired
    private QuotePackageAmounts packageAmounts;
    @Autowired
    private DeliveryAddressMatcher addressMatcher;

    @Transactional(readOnly = true)
    public List<QuotePackageResponse> list(UUID customerId, UUID quoteId) {
        drafts.get(customerId, quoteId);
        return packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(quoteId).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, QuotePackageAmounts.Amount> totals(UUID customerId, UUID quoteId) {
        drafts.get(customerId, quoteId);
        Quote quote = quotes.findById(quoteId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        List<QuoteItem> lines = items.findAllByQuoteIdOrderByCreatedAtAsc(quoteId);
        List<QuotePackage> parcels = packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(quoteId);
        if (lines.isEmpty() || parcels.isEmpty() || lines.stream().anyMatch(line -> !line.isVatRateConfigured())) return Map.of();
        Map<UUID, BigDecimal> quantities = new HashMap<>();
        for (QuotePackage parcel : parcels) {
            for (QuotePackageItem part : allocations.findAllByQuotePackageId(parcel.getId())) {
                quantities.merge(part.getQuoteItemId(), part.getQuantity(), BigDecimal::add);
            }
        }
        if (lines.stream().anyMatch(line -> quantities.getOrDefault(line.getId(), BigDecimal.ZERO).compareTo(line.getQuantity()) != 0)) return Map.of();
        return packageAmounts.calculate(quote, lines, parcels);
    }

    @Transactional
    public QuotePackageResponse save(UUID customerId, UUID quoteId, UUID packageId, QuotePackageRequest request,
                                     DeliveryEstimateService.Estimate estimate) {
        Quote quote = drafts.editableQuote(customerId, quoteId);
        requireUniqueAddress(quoteId, packageId, request);
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
        parcel.setLatitude(request.latitude().setScale(6, RoundingMode.HALF_UP));
        parcel.setLongitude(request.longitude().setScale(6, RoundingMode.HALF_UP));
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

    @Transactional(readOnly = true)
    public void checkAddress(UUID customerId, UUID quoteId, UUID packageId, QuotePackageRequest request) {
        drafts.get(customerId, quoteId);
        requireUniqueAddress(quoteId, packageId, request);
    }

    private void requireUniqueAddress(UUID quoteId, UUID packageId, QuotePackageRequest request) {
        boolean duplicateAddress = packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(quoteId)
                .stream().anyMatch(existing -> !existing.getId().equals(packageId)
                        && addressMatcher.matches(request.city(), request.address(), existing));
        if (duplicateAddress) throw new BusinessException(ErrorCode.QUOTE_DUPLICATE_DELIVERY_ADDRESS);
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
