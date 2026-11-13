package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.response.CreatedOrderResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.InventoryMovement;
import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackage;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackageItem;
import com.se203730.mvc.generateuibe.features.customer.repository.CouponRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.InventoryMovementRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderCreationRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderCreationService {
    private final AccountAccessService accounts;
    private final QuoteRepository quotes;
    private final QuoteItemRepository items;
    private final QuotePackageRepository packages;
    private final QuotePackageItemRepository allocations;
    private final CouponRepository coupons;
    private final QuoteWorkflowService workflow;
    private final ProductInventoryRepository inventory;
    private final InventoryMovementRepository movements;
    private final OrderCreationRepository creation;
    private final OrderRepository orders;
    private final DatabaseClock clock;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CreatedOrderResponse create(UUID customerId, UUID quoteId, int revision) {
        if (accounts.requireActiveUser(customerId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        Quote quote = quotes.findOwnedForUpdate(quoteId, customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (quote.getRevision() != revision) {
            throw new BusinessException(ErrorCode.QUOTE_REVISION_CONFLICT);
        }
        if ("ACCEPTED".equals(quote.getStatus())) {
            UUID orderId = orders.findIdByQuoteAndCustomer(quoteId, customerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT));
            return response(orders.findById(orderId).orElseThrow());
        }
        if (!"DRAFT".equals(quote.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        lockCoupons(quote);

        List<QuoteItem> lines = items.findAllByQuoteIdOrderByCreatedAtAsc(quoteId);
        for (QuoteItem line : lines) {
            if (!line.isVatRateConfigured()) {
                throw new BusinessException(ErrorCode.QUOTE_PRODUCT_VAT_MISSING);
            }
        }
        List<QuotePackage> parcels = new ArrayList<>(
                packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(quoteId));
        parcels.sort(Comparator.comparing(QuotePackage::getId));
        Instant now = clock.now();
        workflow.validateQuote(quote, lines, parcels, now);
        if (quote.getTotalAmount() == null || quote.getTotalAmount().signum() <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
        }
        Map<UUID, Amounts> packageAmounts = calculatePackageAmounts(quote, lines, parcels);
        UUID orderId = UUID.randomUUID();
        creation.insertOrder(orderId, quoteId, now);
        creation.insertItems(orderId, quoteId);

        reserveProducts(lines, orderId, customerId, now);
        for (QuotePackage parcel : parcels) {
            Amounts amount = packageAmounts.get(parcel.getId());
            creation.insertPackage(orderId, parcel.getId(), amount.merchandise, amount.productCoupon,
                    amount.vat, amount.shippingCoupon);
            creation.insertPackageItems(orderId, parcel.getId());
        }
        quote.setStatus("ACCEPTED");
        quote.setUpdatedAt(now);
        quotes.saveAndFlush(quote);
        movements.flush();
        return response(orders.findById(orderId).orElseThrow());
    }

    private void lockCoupons(Quote quote) {
        List<UUID> couponIds = new ArrayList<>();
        if (quote.getCouponId() != null) {
            couponIds.add(quote.getCouponId());
        }
        if (quote.getShippingCouponId() != null && !couponIds.contains(quote.getShippingCouponId())) {
            couponIds.add(quote.getShippingCouponId());
        }
        // Khóa cùng thứ tự giúp giảm nguy cơ hai đơn chờ khóa lẫn nhau.
        Collections.sort(couponIds);
        for (UUID couponId : couponIds) {
            coupons.findForUpdate(couponId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.QUOTE_PRICING_STALE));
        }
    }

    private void reserveProducts(List<QuoteItem> lines, UUID orderId, UUID customerId, Instant now) {
        List<QuoteItem> products = new ArrayList<>(lines);
        products.sort(Comparator.comparing(QuoteItem::getProductId));
        for (QuoteItem line : products) {
            if (inventory.reserve(line.getProductId(), line.getQuantity(), now) != 1) {
                throw new BusinessException(ErrorCode.QUOTE_STOCK_UNAVAILABLE);
            }
            InventoryMovement movement = new InventoryMovement();
            movement.setId(UUID.randomUUID());
            movement.setOrderId(orderId);
            movement.setProductId(line.getProductId());
            movement.setMovementType("RESERVE");
            movement.setQuantity(line.getQuantity());
            movement.setCreatedBy(customerId);
            movement.setCreatedAt(now);
            movements.save(movement);
        }
    }

    private Map<UUID, Amounts> calculatePackageAmounts(Quote quote, List<QuoteItem> lines, List<QuotePackage> parcels) {
        Map<UUID, Amounts> result = new HashMap<>();
        Map<UUID, List<QuotePackageItem>> assigned = new HashMap<>();
        for (QuotePackage parcel : parcels) {
            result.put(parcel.getId(), new Amounts());
            for (QuotePackageItem allocation : allocations.findAllByQuotePackageId(parcel.getId())) {
                UUID itemId = allocation.getQuoteItemId();
                if (!assigned.containsKey(itemId)) {
                    assigned.put(itemId, new ArrayList<>());
                }
                assigned.get(itemId).add(allocation);
            }
        }
        for (QuoteItem line : lines) {
            // Chia số tiền còn lại lần lượt theo số lượng; phần làm tròn được giữ cho các gói sau.
            BigDecimal quantityLeft = line.getQuantity();
            BigDecimal merchandiseLeft = line.getQuantity().multiply(line.getSaleUnitPrice()).setScale(0, RoundingMode.HALF_UP);
            BigDecimal couponLeft = line.getCouponDiscountAmount();
            BigDecimal vatLeft = merchandiseLeft.subtract(couponLeft).multiply(line.getVatRate())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            List<QuotePackageItem> parts = new ArrayList<>(assigned.getOrDefault(line.getId(), List.of()));
            parts.sort(Comparator.comparing(QuotePackageItem::getQuotePackageId));
            for (QuotePackageItem part : parts) {
                BigDecimal merchandise = portion(merchandiseLeft, part.getQuantity(), quantityLeft);
                BigDecimal coupon = portion(couponLeft, merchandise, merchandiseLeft);
                BigDecimal vat = portion(vatLeft, merchandise.subtract(coupon), merchandiseLeft.subtract(couponLeft));
                Amounts amount = result.get(part.getQuotePackageId());
                amount.merchandise = amount.merchandise.add(merchandise);
                amount.productCoupon = amount.productCoupon.add(coupon);
                amount.vat = amount.vat.add(vat);
                quantityLeft = quantityLeft.subtract(part.getQuantity());
                merchandiseLeft = merchandiseLeft.subtract(merchandise);
                couponLeft = couponLeft.subtract(coupon);
                vatLeft = vatLeft.subtract(vat);
            }
        }
        BigDecimal shippingLeft = quote.getShippingFee();
        BigDecimal discountLeft = quote.getShippingCouponDiscountAmount();
        for (QuotePackage parcel : parcels) {
            BigDecimal discount = portion(discountLeft, parcel.getShippingFee(), shippingLeft);
            result.get(parcel.getId()).shippingCoupon = discount;
            shippingLeft = shippingLeft.subtract(parcel.getShippingFee());
            discountLeft = discountLeft.subtract(discount);
        }
        return result;
    }

    private BigDecimal portion(BigDecimal amount, BigDecimal part, BigDecimal total) {
        if (amount.signum() == 0 || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(part).divide(total, 0, RoundingMode.HALF_UP).min(amount);
    }

    private CreatedOrderResponse response(Order order) {
        return new CreatedOrderResponse(order.getId(), order.getOrderNumber(), order.getStatus().name(),
                order.getTotalAmount(), order.getReservationExpiresAt());
    }

    private static class Amounts {
        private BigDecimal merchandise = BigDecimal.ZERO;
        private BigDecimal productCoupon = BigDecimal.ZERO;
        private BigDecimal vat = BigDecimal.ZERO;
        private BigDecimal shippingCoupon = BigDecimal.ZERO;
    }
}
