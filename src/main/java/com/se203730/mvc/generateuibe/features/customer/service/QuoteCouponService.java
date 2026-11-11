package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.request.QuoteCouponsRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteDraftResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.Coupon;
import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import com.se203730.mvc.generateuibe.features.customer.repository.CouponRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class QuoteCouponService {
    @Autowired
    private AccountAccessService accounts;
    @Autowired
    private CouponRepository coupons;
    @Autowired
    private QuoteRepository quotes;
    @Autowired
    private QuoteItemRepository items;
    @Autowired
    private DatabaseClock clock;

    @Transactional
    public QuoteDraftResponse select(UUID customerId, UUID quoteId, QuoteCouponsRequest request) {
        if (accounts.requireActiveUser(customerId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        Quote quote = quotes.findOwnedForUpdate(quoteId, customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"DRAFT".equals(quote.getStatus())) throw new BusinessException(ErrorCode.CONFLICT);
        Instant now = clock.now();
        BigDecimal merchandise = merchandise(quote);
        Coupon product = selectedCoupon(request.productCode(), "PRODUCT", merchandise, now, customerId);
        Coupon shipping = selectedCoupon(request.shippingCode(), "SHIPPING", merchandise, now, customerId);
        quote.setCouponDiscountAmount(BigDecimal.ZERO);
        quote.setShippingCouponDiscountAmount(BigDecimal.ZERO);
        quote.setCouponId(product == null ? null : product.getId());
        quote.setShippingCouponId(shipping == null ? null : shipping.getId());
        apply(quote, now);
        quote.setRevision(quote.getRevision() + 1);
        quote.setUpdatedAt(now);
        quotes.saveAndFlush(quote);
        return QuoteDraftResponse.from(quote, items.findAllByQuoteIdOrderByCreatedAtAsc(quoteId));
    }

    void apply(Quote quote, Instant now) {
        quote.setCouponDiscountAmount(BigDecimal.ZERO);
        quote.setShippingCouponDiscountAmount(BigDecimal.ZERO);
        BigDecimal merchandise = merchandise(quote);
        Coupon product = activeCoupon(quote.getCouponId(), "PRODUCT", merchandise, now, quote.getCustomerId());
        Coupon shipping = activeCoupon(quote.getShippingCouponId(), "SHIPPING", merchandise, now, quote.getCustomerId());
        if (product == null) quote.setCouponId(null);
        if (shipping == null) quote.setShippingCouponId(null);
        BigDecimal productDiscount = discount(product, merchandise);
        BigDecimal shippingDiscount = discount(shipping, quote.getShippingFee());

        BigDecimal remainingDiscount = productDiscount;
        BigDecimal remainingBase = merchandise;
        List<QuoteItem> lines = items.findAllByQuoteIdOrderByCreatedAtAsc(quote.getId());
        for (QuoteItem line : lines) {
            BigDecimal base = line.getQuantity().multiply(line.getSaleUnitPrice()).setScale(0, RoundingMode.HALF_UP);
            BigDecimal part = BigDecimal.ZERO;
            if (remainingBase.signum() > 0 && remainingDiscount.signum() > 0) {
                part = remainingDiscount.multiply(base).divide(remainingBase, 0, RoundingMode.HALF_UP).min(base);
            }
            line.setCouponDiscountAmount(part);
            remainingDiscount = remainingDiscount.subtract(part);
            remainingBase = remainingBase.subtract(base);
        }
        items.saveAll(lines);
        quote.setCouponDiscountAmount(productDiscount);
        quote.setShippingCouponDiscountAmount(shippingDiscount);
        BigDecimal vat = BigDecimal.ZERO;
        for (QuoteItem line : lines) {
            if (line.isVatRateConfigured()) {
                BigDecimal base = line.getQuantity().multiply(line.getSaleUnitPrice())
                        .setScale(0, RoundingMode.HALF_UP).subtract(line.getCouponDiscountAmount());
                vat = vat.add(base.multiply(line.getVatRate())
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP));
            }
        }
        quote.setVatAmount(vat);
    }

    void validateSelection(Quote quote, Instant now) {
        BigDecimal merchandise = merchandise(quote);
        Coupon product = activeCoupon(quote.getCouponId(), "PRODUCT", merchandise, now, quote.getCustomerId());
        Coupon shipping = activeCoupon(quote.getShippingCouponId(), "SHIPPING", merchandise, now, quote.getCustomerId());
        if ((quote.getCouponId() != null && product == null)
                || (quote.getShippingCouponId() != null && shipping == null)
                || discount(product, merchandise).compareTo(quote.getCouponDiscountAmount()) != 0
                || discount(shipping, quote.getShippingFee()).compareTo(quote.getShippingCouponDiscountAmount()) != 0) {
            throw new BusinessException(ErrorCode.QUOTE_PRICING_STALE);
        }
    }

    private Coupon selectedCoupon(String code, String scope, BigDecimal merchandise, Instant now, UUID customerId) {
        if (code == null) return null;
        Coupon coupon = coupons.findByCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
        if (!valid(coupon, scope, merchandise, now, customerId)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return coupon;
    }

    private Coupon activeCoupon(UUID id, String scope, BigDecimal merchandise, Instant now, UUID customerId) {
        if (id == null) return null;
        return coupons.findById(id).filter(coupon -> valid(coupon, scope, merchandise, now, customerId)).orElse(null);
    }

    private boolean valid(Coupon coupon, String scope, BigDecimal merchandise, Instant now, UUID customerId) {
        boolean valid = coupon.isActive() && scope.equals(coupon.getAppliesTo())
                && !now.isBefore(coupon.getStartsAt()) && now.isBefore(coupon.getEndsAt())
                && merchandise.compareTo(coupon.getMinOrderAmount()) >= 0;
        if (!valid) return false;
        if (coupon.getUsageLimit() != null
                && coupons.countUsedOrReserved(coupon.getId(), scope, null) >= coupon.getUsageLimit()) return false;
        return coupon.getUsageLimitPerUser() == null
                || coupons.countUsedOrReserved(coupon.getId(), scope, customerId) < coupon.getUsageLimitPerUser();
    }

    private BigDecimal merchandise(Quote quote) {
        return quote.getItemsSubtotal().subtract(quote.getVolumeDiscountAmount()).subtract(quote.getSaleDiscountAmount());
    }

    private BigDecimal discount(Coupon coupon, BigDecimal base) {
        if (coupon == null || base.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal amount = "PERCENTAGE".equals(coupon.getDiscountType())
                ? base.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                : coupon.getDiscountValue().setScale(0, RoundingMode.HALF_UP);
        if (coupon.getMaxDiscountAmount() != null) amount = amount.min(coupon.getMaxDiscountAmount());
        return amount.min(base);
    }
}
