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
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
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
    @Autowired
    private QuoteResponseFactory responses;

    public record EligibleProduct(UUID id, String nameVi, String nameEn) {}
    public record SavedCoupon(String code, String scope, String nameVi, String nameEn,
            String discountType, BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minOrderAmount,
            Integer usageLimitPerUser, Integer usageLimit, long used, long reserved, Long remaining,
            Instant startsAt, Instant endsAt, String productScope, List<EligibleProduct> products,
            boolean valid, String reason) {}

    private Quote ownedQuote(UUID customerId, UUID quoteId) {
        if (accounts.requireActiveUser(customerId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return quotes.findById(quoteId).filter(q -> customerId.equals(q.getCustomerId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<SavedCoupon> reusable(UUID customerId, UUID quoteId) {
        Quote quote = ownedQuote(customerId, quoteId);
        return describe(quote, coupons.previouslyEntered(customerId));
    }

    @Transactional
    public SavedCoupon inspect(UUID customerId, UUID quoteId, String code) {
        Quote quote = ownedQuote(customerId, quoteId);
        if (code == null || !code.matches("[A-Za-z0-9]{8}")) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        Coupon coupon = coupons.findByCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
        coupons.remember(customerId, coupon.getId(), coupon.getCode());
        return describe(quote, List.of(coupon)).getFirst();
    }

    private List<SavedCoupon> describe(Quote quote, List<Coupon> known) {
        if (known.isEmpty()) return List.of();
        var ids = known.stream().map(Coupon::getId).toList();
        Map<UUID, CouponRepository.Usage> usage = new HashMap<>();
        coupons.usage(ids, quote.getCustomerId()).forEach(row -> usage.put(row.getCouponId(), row));
        Map<UUID, List<EligibleProduct>> scopes = new HashMap<>();
        for (var row : coupons.scopedProducts(ids)) {
            scopes.computeIfAbsent(row.getCouponId(), ignored -> new java.util.ArrayList<>())
                    .add(new EligibleProduct(row.getProductId(), row.getNameVi(), row.getNameEn()));
        }
        Set<UUID> paidLines = new HashSet<>();
        items.findAllByQuoteIdOrderByCreatedAtAsc(quote.getId()).stream()
                .filter(line -> lineBase(line).signum() > 0).forEach(line -> paidLines.add(line.getProductId()));
        Instant now = clock.now();
        return known.stream().map(c -> {
            var counts = usage.get(c.getId());
            long used = counts == null ? 0 : counts.getUsed();
            long reserved = counts == null ? 0 : counts.getReserved();
            Long remaining = c.getUsageLimitPerUser() == null ? null
                    : Math.max(0L, c.getUsageLimitPerUser() - used - reserved);
            if (c.getUsageLimit() != null) {
                long globalRemaining = Math.max(0L, c.getUsageLimit() - (counts == null ? 0 : counts.getTotal()));
                remaining = remaining == null ? globalRemaining : Math.min(remaining, globalRemaining);
            }
            var products = scopes.getOrDefault(c.getId(), List.of());
            String reason = reason(c, merchandise(quote), now, counts);
            if (reason == null && "PRODUCT".equals(c.getAppliesTo()) && (paidLines.isEmpty()
                    || ("SELECTED".equals(c.getProductScope()) && products.stream().noneMatch(p -> paidLines.contains(p.id()))))) {
                reason = "PRODUCT_SCOPE";
            }
            return new SavedCoupon(c.getCode(), c.getAppliesTo(), c.getNameVi(), c.getNameEn(), c.getDiscountType(),
                    c.getDiscountValue(), c.getMaxDiscountAmount(), c.getMinOrderAmount(), c.getUsageLimitPerUser(), c.getUsageLimit(),
                    used, reserved, remaining, c.getStartsAt(), c.getEndsAt(), c.getProductScope(), products,
                    reason == null, reason);
        }).toList();
    }

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
        if (product != null && eligibleBase(product, items.findAllByQuoteIdOrderByCreatedAtAsc(quoteId)).signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (product != null) coupons.remember(customerId, product.getId(), product.getCode());
        if (shipping != null) coupons.remember(customerId, shipping.getId(), shipping.getCode());
        quote.setCouponDiscountAmount(BigDecimal.ZERO);
        quote.setShippingCouponDiscountAmount(BigDecimal.ZERO);
        quote.setCouponId(product == null ? null : product.getId());
        quote.setShippingCouponId(shipping == null ? null : shipping.getId());
        apply(quote, now);
        quote.setRevision(quote.getRevision() + 1);
        quote.setUpdatedAt(now);
        quotes.saveAndFlush(quote);
        return responses.create(quote, items.findAllByQuoteIdOrderByCreatedAtAsc(quoteId));
    }

    void apply(Quote quote, Instant now) {
        quote.setCouponDiscountAmount(BigDecimal.ZERO);
        quote.setShippingCouponDiscountAmount(BigDecimal.ZERO);
        BigDecimal merchandise = merchandise(quote);
        Coupon product = activeCoupon(quote.getCouponId(), "PRODUCT", merchandise, now, quote.getCustomerId());
        Coupon shipping = activeCoupon(quote.getShippingCouponId(), "SHIPPING", merchandise, now, quote.getCustomerId());
        if (product == null) quote.setCouponId(null);
        if (shipping == null) quote.setShippingCouponId(null);
        List<QuoteItem> lines = items.findAllByQuoteIdOrderByCreatedAtAsc(quote.getId());
        BigDecimal eligibleBase = eligibleBase(product, lines);
        if (product != null && eligibleBase.signum() <= 0) {
            product = null;
            quote.setCouponId(null);
        }
        BigDecimal productDiscount = discount(product, eligibleBase);
        BigDecimal shippingDiscount = discount(shipping, quote.getShippingFee());

        Map<UUID, BigDecimal> allocations = allocations(product, lines, productDiscount);
        for (QuoteItem line : lines) {
            line.setCouponDiscountAmount(allocations.getOrDefault(line.getId(), BigDecimal.ZERO));
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
        List<QuoteItem> lines = items.findAllByQuoteIdOrderByCreatedAtAsc(quote.getId());
        BigDecimal base = eligibleBase(product, lines);
        BigDecimal productDiscount = discount(product, base);
        Map<UUID, BigDecimal> expected = allocations(product, lines, productDiscount);
        if ((quote.getCouponId() != null && product == null)
                || (quote.getShippingCouponId() != null && shipping == null)
                || (product != null && base.signum() <= 0)
                || productDiscount.compareTo(quote.getCouponDiscountAmount()) != 0
                || lines.stream().anyMatch(line -> expected.getOrDefault(line.getId(), BigDecimal.ZERO)
                        .compareTo(line.getCouponDiscountAmount()) != 0)
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
        if (!scope.equals(coupon.getAppliesTo())) return false;
        var usage = coupon.getUsageLimit() == null && coupon.getUsageLimitPerUser() == null ? null
                : coupons.usage(List.of(coupon.getId()), customerId).stream().findFirst().orElse(null);
        return reason(coupon, merchandise, now, usage) == null;
    }

    private String reason(Coupon coupon, BigDecimal merchandise, Instant now, CouponRepository.Usage usage) {
        if (!coupon.isActive()) return "INACTIVE";
        if (now.isBefore(coupon.getStartsAt())) return "NOT_STARTED";
        if (!now.isBefore(coupon.getEndsAt())) return "EXPIRED";
        if (usage != null && coupon.getUsageLimit() != null && usage.getTotal() >= coupon.getUsageLimit()) return "GLOBAL_LIMIT";
        if (usage != null && coupon.getUsageLimitPerUser() != null
                && usage.getUsed() + usage.getReserved() >= coupon.getUsageLimitPerUser()) return "USER_LIMIT";
        if (merchandise.compareTo(coupon.getMinOrderAmount()) < 0) return "MINIMUM";
        return null;
    }

    private BigDecimal merchandise(Quote quote) {
        return quote.getItemsSubtotal().subtract(quote.getVolumeDiscountAmount()).subtract(quote.getSaleDiscountAmount());
    }

    private Set<UUID> eligibleProducts(Coupon coupon) {
        return coupon != null && "SELECTED".equals(coupon.getProductScope())
                ? new HashSet<>(coupons.findProductIds(coupon.getId())) : null;
    }

    private BigDecimal lineBase(QuoteItem line) {
        return line.getQuantity().multiply(line.getSaleUnitPrice()).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal eligibleBase(Coupon coupon, List<QuoteItem> lines) {
        if (coupon == null) return BigDecimal.ZERO;
        Set<UUID> eligible = eligibleProducts(coupon);
        return lines.stream().filter(line -> eligible == null || eligible.contains(line.getProductId()))
                .map(this::lineBase).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<UUID, BigDecimal> allocations(Coupon coupon, List<QuoteItem> lines, BigDecimal discount) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (coupon == null) return result;
        Set<UUID> eligible = eligibleProducts(coupon);
        BigDecimal remainingBase = lines.stream()
                .filter(line -> eligible == null || eligible.contains(line.getProductId()))
                .map(this::lineBase).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingDiscount = discount;
        for (QuoteItem line : lines) {
            if (eligible != null && !eligible.contains(line.getProductId())) continue;
            BigDecimal base = lineBase(line);
            BigDecimal part = remainingBase.signum() > 0
                    ? remainingDiscount.multiply(base).divide(remainingBase, 0, RoundingMode.HALF_UP).min(base)
                    : BigDecimal.ZERO;
            result.put(line.getId(), part);
            remainingDiscount = remainingDiscount.subtract(part);
            remainingBase = remainingBase.subtract(base);
        }
        return result;
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
