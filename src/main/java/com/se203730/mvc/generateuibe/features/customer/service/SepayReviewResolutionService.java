package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.exception.ErrorResponse;
import com.se203730.mvc.generateuibe.common.outbox.service.OutboxEventService;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.dto.request.ResolvePaymentReviewRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.response.PaymentReviewResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.InventoryMovement;
import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.entity.Payment;
import com.se203730.mvc.generateuibe.features.customer.entity.SepayWebhookEvent;
import com.se203730.mvc.generateuibe.features.customer.enums.OrderStatus;
import com.se203730.mvc.generateuibe.features.customer.repository.InventoryMovementRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.PaymentRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.SepayWebhookEventRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trang Admin "Thanh toán cần xử lý": các khoản SePay đã vào tài khoản nhưng không tự khớp đơn
 * (hết hạn giữ hàng, sai số tiền, sai nội dung, trả trùng…). Admin chọn hoàn tiền, khôi phục đơn hoặc bỏ qua.
 */
@Service
@RequiredArgsConstructor
public class SepayReviewResolutionService {
    private final SepayWebhookEventRepository events;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final ProductInventoryRepository inventory;
    private final InventoryMovementRepository movements;
    private final DatabaseClock clock;
    private final OutboxEventService outbox;

    @Transactional(readOnly = true)
    public Page<PaymentReviewResponse> list(boolean resolved, int page, int size) {
        return events.findReviews(resolved, PageRequest.of(page, size)).map(PaymentReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return events.countOpenReviews();
    }

    // SERIALIZABLE như lúc tạo đơn: giữ hàng lại cạnh tranh với khách khác đang đặt cùng sản phẩm.
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void resolve(UUID adminId, Long transactionId, ResolvePaymentReviewRequest request) {
        SepayWebhookEvent event = events.findForUpdate(transactionId)
                .filter(found -> "REVIEW".equals(found.getResult()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (event.getResolution() != null) {
            throw new BusinessException(ErrorCode.PAYMENT_REVIEW_ALREADY_RESOLVED);
        }
        Instant now = clock.now();
        switch (request.action()) {
            case REFUNDED -> {
                if (request.refundReference() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
                event.setRefundReference(request.refundReference());
            }
            case IGNORED -> {
                if (request.note() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
            }
            case RESTORED -> restoreOrder(event, now);
        }
        event.setResolution(request.action().name());
        event.setResolutionNote(request.note());
        event.setResolvedBy(adminId);
        event.setResolvedAt(now);
    }

    private void restoreOrder(SepayWebhookEvent event, Instant now) {
        UUID paymentId = event.getPaymentId();
        if (paymentId == null && event.getPaymentCode() != null) {
            paymentId = payments.findByPaymentNumberForUpdate(event.getPaymentCode()).map(Payment::getId).orElse(null);
        }
        if (paymentId == null) {
            throw new BusinessException(ErrorCode.PAYMENT_REVIEW_NOT_RESTORABLE);
        }
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_REVIEW_NOT_RESTORABLE));
        Order order = orders.findForUpdate(payment.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_REVIEW_NOT_RESTORABLE));
        boolean stillReserved = order.getStatus() == OrderStatus.CONFIRMED && order.getReservationReleasedAt() == null;
        if (!"SEPAY".equals(payment.getProvider()) || !"BANK_TRANSFER".equals(payment.getPaymentMethod())
                || "SUCCESS".equals(payment.getStatus())
                || payments.existsByOrderIdAndStatus(order.getId(), "SUCCESS")
                || (order.getStatus() != OrderStatus.CANCELLED && !stillReserved)
                || event.getAmount().compareTo(payment.getAmount()) != 0
                || order.getTotalAmount() == null || order.getTotalAmount().compareTo(payment.getAmount()) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_REVIEW_NOT_RESTORABLE);
        }
        if (!stillReserved) {
            reserveAgain(order, now);
        }
        payment.setStatus("SUCCESS");
        payment.setProviderTransactionId(event.getTransactionId().toString());
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);
        payments.saveAndFlush(payment);
        if (orders.restorePaid(order.getId(), now) != 1) {
            throw new BusinessException(ErrorCode.PAYMENT_REVIEW_NOT_RESTORABLE);
        }
        event.setPaymentId(payment.getId());
        outbox.record("payment.succeeded", "PAYMENT", payment.getId(), Map.of(
                "orderId", order.getId(),
                "customerId", order.getCustomerId(),
                "paymentNumber", payment.getPaymentNumber(),
                "amount", payment.getAmount(),
                "paidAt", now,
                "restoredFromReview", event.getTransactionId()));
    }

    /** Đơn đã trả hàng về kho khi hết hạn/huỷ: giữ lại đủ số lượng, thiếu thì báo từng sản phẩm. */
    private void reserveAgain(Order order, Instant now) {
        List<OrderRepository.ProductQuantity> lines = orders.findProductQuantities(order.getId());
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PAYMENT_REVIEW_NOT_RESTORABLE);
        }
        List<ErrorResponse.ValidationError> shortages = new ArrayList<>();
        for (OrderRepository.ProductQuantity line : lines) {
            if (inventory.reserve(line.getProductId(), line.getQuantity(), now) != 1) {
                BigDecimal onHand = inventory.findById(line.getProductId())
                        .map(stock -> stock.getAvailableQuantity())
                        .orElse(BigDecimal.ZERO);
                shortages.add(StockShortage.of(line.getProductId(), onHand, line.getQuantity()));
                continue;
            }
            InventoryMovement movement = new InventoryMovement();
            movement.setId(UUID.randomUUID());
            movement.setOrderId(order.getId());
            movement.setProductId(line.getProductId());
            movement.setMovementType("RESERVE");
            movement.setQuantity(line.getQuantity());
            movement.setReason("Restored from a late SePay payment");
            movement.setCreatedBy(order.getCustomerId());
            movement.setCreatedAt(now);
            movements.save(movement);
        }
        if (!shortages.isEmpty()) {
            throw new BusinessException(ErrorCode.QUOTE_STOCK_UNAVAILABLE, shortages);
        }
    }
}
