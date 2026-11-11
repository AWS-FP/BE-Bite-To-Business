package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.request.UpdateQuoteItemRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteDraftResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteSummaryResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class QuoteDraftService {
    @Autowired
    private QuoteRepository quotes;
    @Autowired
    private QuoteItemRepository items;
    @Autowired
    private QuotePriceService prices;
    @Autowired
    private UserRepository users;
    @Autowired
    private AccountAccessService accounts;
    @Autowired
    private DatabaseClock clock;
    @Autowired
    private QuoteCouponService couponService;

    @Transactional(readOnly = true)
    public Page<QuoteSummaryResponse> list(UUID customerId, int page, int size) {
        requireCustomer(customerId);
        return quotes.findByCustomerIdOrderByUpdatedAtDesc(customerId, PageRequest.of(page, size))
                .map(QuoteSummaryResponse::from);
    }

    @Transactional
    public QuoteDraftResponse getOrCreate(UUID customerId) {
        users.findByIdForUpdate(customerId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        requireCustomer(customerId);
        Quote quote = quotes.findFirstByCustomerIdAndStatusOrderByUpdatedAtDesc(customerId, "DRAFT")
                .orElseGet(() -> newDraft(customerId));
        return response(quote);
    }

    @Transactional(readOnly = true)
    public QuoteDraftResponse get(UUID customerId, UUID quoteId) {
        requireCustomer(customerId);
        Quote quote = quotes.findById(quoteId).filter(q -> q.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return response(quote);
    }

    @Transactional
    public QuoteDraftResponse putItem(UUID customerId, UUID quoteId, UpdateQuoteItemRequest request) {
        Quote quote = editableQuote(customerId, quoteId);
        QuotePriceService.Price current = prices.calculate(request.productId(), request.quantity());
        var price = current.product();
        Instant now = clock.now();
        QuoteItem item = items.findByQuoteIdAndProductId(quoteId, request.productId()).orElse(null);
        if (item == null) {
            item = new QuoteItem();
            item.setId(UUID.randomUUID());
            item.setQuoteId(quoteId);
            item.setProductId(request.productId());
            item.setCreatedAt(now);
        } else if (items.hasPackageAllocations(item.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        item.setPriceTierId(price.getTierId());
        item.setSku(price.getSku());
        item.setNameVi(price.getNameVi());
        item.setNameEn(price.getNameEn());
        item.setUnitCode(price.getUnitCode());
        item.setQuantity(request.quantity());
        item.setListUnitPrice(price.getListPrice());
        item.setVolumeUnitPrice(current.volume());
        item.setSaleUnitPrice(current.sale());
        item.setCouponDiscountAmount(BigDecimal.ZERO);
        setVatRate(item, price.getVatRate());
        item.setUpdatedAt(now);
        items.saveAndFlush(item);
        recalculate(quote, now);
        return response(quote);
    }

    @Transactional
    public QuoteDraftResponse removeItem(UUID customerId, UUID quoteId, UUID itemId) {
        Quote quote = editableQuote(customerId, quoteId);
        QuoteItem item = items.findByIdAndQuoteId(itemId, quoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (items.hasPackageAllocations(itemId)) throw new BusinessException(ErrorCode.CONFLICT);
        items.delete(item);
        items.flush();
        recalculate(quote, clock.now());
        return response(quote);
    }

    @Transactional
    public QuoteDraftResponse refreshPrices(UUID customerId, UUID quoteId) {
        Quote quote = editableQuote(customerId, quoteId);
        Instant now = clock.now();
        for (QuoteItem item : items.findAllByQuoteIdOrderByCreatedAtAsc(quoteId)) {
            QuotePriceService.Price current = prices.calculate(item.getProductId(), item.getQuantity());
            var product = current.product();
            item.setPriceTierId(product.getTierId());
            item.setSku(product.getSku());
            item.setNameVi(product.getNameVi());
            item.setNameEn(product.getNameEn());
            item.setUnitCode(product.getUnitCode());
            item.setListUnitPrice(product.getListPrice());
            item.setVolumeUnitPrice(current.volume());
            item.setSaleUnitPrice(current.sale());
            item.setCouponDiscountAmount(BigDecimal.ZERO);
            setVatRate(item, product.getVatRate());
            item.setUpdatedAt(now);
            items.save(item);
        }
        items.flush();
        recalculate(quote, now);
        return response(quote);
    }

    private Quote newDraft(UUID customerId) {
        Instant now = clock.now();
        Quote quote = new Quote();
        quote.setId(UUID.randomUUID());
        quote.setQuoteNumber("Q-" + quote.getId().toString().toUpperCase());
        quote.setCustomerId(customerId);
        quote.setStatus("DRAFT");
        quote.setRevision(1);
        quote.setItemsSubtotal(BigDecimal.ZERO);
        quote.setVolumeDiscountAmount(BigDecimal.ZERO);
        quote.setSaleDiscountAmount(BigDecimal.ZERO);
        quote.setCouponDiscountAmount(BigDecimal.ZERO);
        quote.setShippingCouponDiscountAmount(BigDecimal.ZERO);
        quote.setVatAmount(BigDecimal.ZERO);
        quote.setShippingFee(BigDecimal.ZERO);
        quote.setCreatedAt(now);
        quote.setUpdatedAt(now);
        return quotes.saveAndFlush(quote);
    }

    Quote editableQuote(UUID customerId, UUID quoteId) {
        requireCustomer(customerId);
        Quote quote = quotes.findOwnedForUpdate(quoteId, customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"DRAFT".equals(quote.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return quote;
    }

    private void recalculate(Quote quote, Instant now) {
        List<QuoteItem> rows = items.findAllByQuoteIdOrderByCreatedAtAsc(quote.getId());
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal volumeDiscount = BigDecimal.ZERO;
        BigDecimal saleDiscount = BigDecimal.ZERO;
        for (QuoteItem item : rows) {
            BigDecimal list = line(item.getQuantity(), item.getListUnitPrice());
            BigDecimal volume = line(item.getQuantity(), item.getVolumeUnitPrice());
            BigDecimal sale = line(item.getQuantity(), item.getSaleUnitPrice());
            subtotal = subtotal.add(list);
            volumeDiscount = volumeDiscount.add(list.subtract(volume));
            saleDiscount = saleDiscount.add(volume.subtract(sale));
        }
        quote.setItemsSubtotal(subtotal);
        quote.setVolumeDiscountAmount(volumeDiscount);
        quote.setSaleDiscountAmount(saleDiscount);
        quote.setCouponDiscountAmount(BigDecimal.ZERO);
        quote.setShippingCouponDiscountAmount(BigDecimal.ZERO);
        couponService.apply(quote, now);
        quote.setRevision(quote.getRevision() + 1);
        quote.setUpdatedAt(now);
        quotes.saveAndFlush(quote);
    }

    private BigDecimal line(BigDecimal quantity, BigDecimal price) {
        return quantity.multiply(price).setScale(0, RoundingMode.HALF_UP);
    }

    private void setVatRate(QuoteItem item, BigDecimal rate) {
        item.setVatRateConfigured(rate != null);
        item.setVatRate(rate == null ? BigDecimal.ZERO : rate);
    }

    private QuoteDraftResponse response(Quote quote) {
        return QuoteDraftResponse.from(quote, items.findAllByQuoteIdOrderByCreatedAtAsc(quote.getId()));
    }

    private void requireCustomer(UUID customerId) {
        if (accounts.requireActiveUser(customerId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
