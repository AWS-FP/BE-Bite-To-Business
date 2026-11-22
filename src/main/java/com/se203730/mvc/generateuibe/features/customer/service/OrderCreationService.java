package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.outbox.service.OutboxEventService;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.response.CreatedOrderResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.InventoryMovement;
import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackage;
import com.se203730.mvc.generateuibe.features.customer.repository.CouponRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.InventoryMovementRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderCreationRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
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
    private final CouponRepository coupons;
    private final QuoteWorkflowService workflow;
    private final ProductInventoryRepository inventory;
    private final InventoryMovementRepository movements;
    private final OrderCreationRepository creation;
    private final OrderRepository orders;
    private final DatabaseClock clock;
    private final QuotePackageAmounts packageAmountsCalculator;
    private final OutboxEventService outbox;

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
        boolean approved = "APPROVED".equals(quote.getStatus())
            && Integer.valueOf(revision).equals(quote.getReviewedRevision());
        if (approved && quote.getExpiresAt() != null && !quote.getExpiresAt().isAfter(clock.now())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        if (!"DRAFT".equals(quote.getStatus()) && !approved) {
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
        Map<UUID, QuotePackageAmounts.Amount> packageAmounts = packageAmountsCalculator.calculate(quote, lines, parcels);
        UUID orderId = UUID.randomUUID();
        creation.insertOrder(orderId, quoteId, now);
        creation.insertItems(orderId, quoteId);

        reserveProducts(lines, orderId, customerId, now);
        for (QuotePackage parcel : parcels) {
            QuotePackageAmounts.Amount amount = packageAmounts.get(parcel.getId());
            creation.insertPackage(orderId, parcel.getId(), amount.merchandise(), amount.productCoupon(),
                amount.vat(), amount.shippingCoupon());
            creation.insertPackageItems(orderId, parcel.getId());
        }
        quote.setStatus("ACCEPTED");
        quote.setUpdatedAt(now);
        quotes.saveAndFlush(quote);
        movements.flush();
        Order order = orders.findById(orderId).orElseThrow();
        outbox.record("order.created", "ORDER", orderId, Map.of(
                "customerId", customerId,
                "quoteId", quoteId,
                "totalAmount", order.getTotalAmount(),
                "reservationExpiresAt", String.valueOf(order.getReservationExpiresAt())));
        return response(order);
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

    private CreatedOrderResponse response(Order order) {
        return new CreatedOrderResponse(order.getId(), order.getOrderNumber(), order.getStatus().name(),
            order.getTotalAmount(), order.getReservationExpiresAt());
    }

}
