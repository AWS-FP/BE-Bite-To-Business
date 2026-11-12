package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteDraftResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteAvailabilityResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackage;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackageItem;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.entity.ProductInventory;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.repository.ProductInventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QuoteWorkflowService {
    @Autowired
    private QuoteDraftService drafts;
    @Autowired
    private QuoteItemRepository items;
    @Autowired
    private QuotePackageRepository packages;
    @Autowired
    private QuotePackageItemRepository allocations;
    @Autowired
    private QuotePriceService prices;
    @Autowired
    private QuoteCouponService coupons;
    @Autowired
    private DatabaseClock clock;
    @Autowired
    private ProductInventoryRepository inventory;

    @Transactional(readOnly = true)
    public QuoteAvailabilityResponse availability(UUID customerId, UUID quoteId) {
        drafts.get(customerId, quoteId);
        List<QuoteItem> lines = items.findAllByQuoteIdOrderByCreatedAtAsc(quoteId);
        Map<UUID, ProductInventory> available = getInventory(lines);
        List<QuoteAvailabilityResponse.Item> result = lines.stream().map(line -> {
            ProductInventory stock = available.get(line.getProductId());
            BigDecimal quantity = stock == null ? null : stock.getAvailableQuantity();
            return new QuoteAvailabilityResponse.Item(line.getProductId(), line.getSku(), line.getQuantity(),
                    quantity, quantity != null && quantity.compareTo(line.getQuantity()) >= 0);
        }).toList();
        return new QuoteAvailabilityResponse(!result.isEmpty() && result.stream().allMatch(QuoteAvailabilityResponse.Item::available), result);
    }

    @Transactional
    public QuoteDraftResponse validatePricing(UUID customerId, UUID quoteId, int expectedRevision) {
        Quote quote = drafts.editableQuote(customerId, quoteId);
        if (quote.getRevision() != expectedRevision) {
            throw new BusinessException(ErrorCode.QUOTE_REVISION_CONFLICT);
        }
        List<QuoteItem> lines = items.findAllByQuoteIdOrderByCreatedAtAsc(quoteId);
        if (lines.stream().anyMatch(item -> !item.isVatRateConfigured())) {
            throw new BusinessException(ErrorCode.QUOTE_PRODUCT_VAT_MISSING);
        }
        List<QuotePackage> parcels = packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(quoteId);
        validateQuote(quote, lines, parcels, clock.now());
        checkStock(lines);
        return QuoteDraftResponse.from(quote, lines);
    }

    private void checkStock(List<QuoteItem> lines) {
        Map<UUID, ProductInventory> available = getInventory(lines);
        for (QuoteItem line : lines) {
            ProductInventory stock = available.get(line.getProductId());
            if (stock == null || stock.getAvailableQuantity().compareTo(line.getQuantity()) < 0) {
                throw new BusinessException(ErrorCode.QUOTE_STOCK_UNAVAILABLE);
            }
        }
    }

    private Map<UUID, ProductInventory> getInventory(List<QuoteItem> lines) {
        List<UUID> productIds = lines.stream().map(QuoteItem::getProductId).toList();
        return inventory.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductInventory::getProductId, item -> item));
    }

    public void validateQuote(Quote quote, List<QuoteItem> lines, List<QuotePackage> parcels, Instant now) {
        if (quote.getContactName() == null || quote.getContactName().isBlank()
                || quote.getContactPhone() == null || quote.getContactPhone().isBlank()
                || quote.getContactEmail() == null || quote.getContactEmail().isBlank()) {
            throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
        }
        if (lines.isEmpty() || parcels.isEmpty()) throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
        Map<UUID, BigDecimal> quantities = new HashMap<>();
        BigDecimal shipping = BigDecimal.ZERO;
        for (QuotePackage parcel : parcels) {
            if (parcel.getShippingFee() == null || parcel.getDistanceMetres() == null
                    || parcel.getRecipientName() == null || parcel.getRecipientName().isBlank()
                    || parcel.getRecipientPhone() == null || parcel.getRecipientPhone().isBlank()
                    || parcel.getDeliveryTimeSlot() == null || parcel.getDeliveryTimeSlot().isBlank()
                    || parcel.getLatitude() == null || parcel.getLongitude() == null
                    || parcel.getCity() == null || "NEEDS_UPDATE".equals(parcel.getAddressStatus())) {
                throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
            }
            List<QuotePackageItem> assigned = allocations.findAllByQuotePackageId(parcel.getId());
            if (assigned.isEmpty()) throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
            for (QuotePackageItem allocation : assigned) {
                quantities.merge(allocation.getQuoteItemId(), allocation.getQuantity(), BigDecimal::add);
            }
            shipping = shipping.add(parcel.getShippingFee());
        }
        if (shipping.compareTo(quote.getShippingFee()) != 0) {
            throw new BusinessException(ErrorCode.QUOTE_PRICING_STALE);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal volumeDiscount = BigDecimal.ZERO;
        BigDecimal saleDiscount = BigDecimal.ZERO;
        BigDecimal couponDiscount = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        for (QuoteItem item : lines) {
            if (quantities.getOrDefault(item.getId(), BigDecimal.ZERO).compareTo(item.getQuantity()) != 0) {
                throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
            }
            QuotePriceService.Price current;
            try {
                current = prices.calculate(item.getProductId(), item.getQuantity());
            } catch (BusinessException exception) {
                throw new BusinessException(ErrorCode.QUOTE_PRICING_STALE);
            }
            if (item.getListUnitPrice().compareTo(current.product().getListPrice()) != 0
                    || item.getVolumeUnitPrice().compareTo(current.volume()) != 0
                    || item.getSaleUnitPrice().compareTo(current.sale()) != 0
                    || !Objects.equals(item.getPriceTierId(), current.product().getTierId())) {
                throw new BusinessException(ErrorCode.QUOTE_PRICING_STALE);
            }
            BigDecimal currentVatRate = current.product().getVatRate();
            if (item.isVatRateConfigured() != (currentVatRate != null)
                    || (currentVatRate != null && item.getVatRate().compareTo(currentVatRate) != 0)) {
                throw new BusinessException(ErrorCode.QUOTE_PRICING_STALE);
            }
            BigDecimal list = line(item.getQuantity(), item.getListUnitPrice());
            BigDecimal volume = line(item.getQuantity(), item.getVolumeUnitPrice());
            BigDecimal sale = line(item.getQuantity(), item.getSaleUnitPrice());
            subtotal = subtotal.add(list);
            volumeDiscount = volumeDiscount.add(list.subtract(volume));
            saleDiscount = saleDiscount.add(volume.subtract(sale));
            couponDiscount = couponDiscount.add(item.getCouponDiscountAmount());
            if (item.isVatRateConfigured()) {
                vat = vat.add(sale.subtract(item.getCouponDiscountAmount()).multiply(item.getVatRate())
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP));
            }
        }
        if (subtotal.compareTo(quote.getItemsSubtotal()) != 0
                || volumeDiscount.compareTo(quote.getVolumeDiscountAmount()) != 0
                || saleDiscount.compareTo(quote.getSaleDiscountAmount()) != 0
                || couponDiscount.compareTo(quote.getCouponDiscountAmount()) != 0
                || vat.compareTo(quote.getVatAmount()) != 0) {
            throw new BusinessException(ErrorCode.QUOTE_PRICING_STALE);
        }
        coupons.validateSelection(quote, now);
    }

    private BigDecimal line(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
    }
}
