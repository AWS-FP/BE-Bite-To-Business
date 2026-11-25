package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteDraftResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.CouponRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class QuoteResponseFactory {
    private final QuoteItemRepository items;
    private final CouponRepository coupons;

    public QuoteResponseFactory(QuoteItemRepository items, CouponRepository coupons) {
        this.items = items;
        this.coupons = coupons;
    }

    public QuoteDraftResponse create(Quote quote, List<QuoteItem> lines) {
        Map<UUID, BigDecimal> steps = new HashMap<>();
        Map<UUID, BigDecimal> minimums = new HashMap<>();
        for (QuoteItemRepository.QuantityStep row : items.findQuantitySteps(quote.getId())) {
            steps.put(row.getProductId(), row.getQuantityStep());
            minimums.put(row.getProductId(), row.getMinOrderQuantity());
        }
        // Only disclose already-applied codes to the owner of this quote.
        String productCode = quote.getCouponId() == null ? null :
                coupons.findById(quote.getCouponId()).map(c -> c.getCode()).orElse(null);
        String shippingCode = quote.getShippingCouponId() == null ? null :
                coupons.findById(quote.getShippingCouponId()).map(c -> c.getCode()).orElse(null);
        return QuoteDraftResponse.from(quote, lines, steps, minimums, productCode, shippingCode);
    }
}
