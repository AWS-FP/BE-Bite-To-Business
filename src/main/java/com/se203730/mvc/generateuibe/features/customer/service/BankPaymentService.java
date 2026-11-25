package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.outbox.service.OutboxEventService;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.dto.request.SepayWebhookRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.BankPaymentResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.entity.Payment;
import com.se203730.mvc.generateuibe.features.customer.entity.SepayWebhookEvent;
import com.se203730.mvc.generateuibe.features.customer.enums.OrderStatus;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.PaymentRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.SepayWebhookEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BankPaymentService {
    private static final Pattern PAYMENT_CODE = Pattern.compile("(?<![A-Z0-9])BTB[0-9A-F]{12}(?![A-Z0-9])");

    @Autowired
    private OrderRepository orders;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private SepayWebhookEventRepository events;

    @Autowired
    private DatabaseClock clock;

    @Autowired
    private OutboxEventService outbox;

    @Value("${app.sepay.bank-account:}")
    private String bankAccount;

    @Value("${app.sepay.virtual-account:}")
    private String virtualAccount;

    @Value("${app.sepay.webhook-secret:}")
    private String webhookSecret;

    @Transactional
    public BankPaymentResponse create(UUID customerId, UUID orderId) {
        requireConfiguration();
        Order order = orders.findForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireOwner(order, customerId);
        if (payments.existsByOrderIdAndStatus(orderId, "SUCCESS")) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
        }
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
        }
        if (reservationExpired(order, clock.now())) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
        }
        Payment payment = payments.findByOrderIdAndStatus(orderId, "PENDING").orElse(null);
        if (payment != null) {
            if (!"SEPAY".equals(payment.getProvider()) || !"BANK_TRANSFER".equals(payment.getPaymentMethod())) {
                throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
            }
            return response(payment, true);
        }
        Instant now = clock.now();
        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setPaymentNumber("BTB" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT));
        payment.setOrderId(orderId);
        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setProvider("SEPAY");
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency("VND");
        payment.setStatus("PENDING");
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payments.saveAndFlush(payment);
        return response(payment, true);
    }

    @Transactional(readOnly = true)
    public BankPaymentResponse get(UUID customerId, UUID paymentId) {
        requireConfiguration();
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        Order order = orders.findById(payment.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireOwner(order, customerId);
        if (!"SEPAY".equals(payment.getProvider()) || !"BANK_TRANSFER".equals(payment.getPaymentMethod())) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        boolean payable = (order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.PROCESSING) && !reservationExpired(order, clock.now());
        return response(payment, payable);
    }

    @Transactional
    public void receive(SepayWebhookRequest request) {
        receive(request, "WEBHOOK", null);
    }

    /**
     * Ghi nhận một giao dịch SePay. Idempotent theo transaction id và mã tham chiếu, nên webhook
     * và job đối soát có thể cùng gửi một giao dịch mà không bị ghi hai lần.
     *
     * @param source  WEBHOOK hoặc RECONCILIATION
     * @param paidAt  thời điểm ngân hàng ghi nhận (job đối soát biết); null thì dùng giờ hiện tại.
     *                Hạn giữ hàng được so với thời điểm này, để khoản trả đúng hạn nhưng webhook bị sót
     *                vẫn khớp đơn nếu đơn chưa bị huỷ.
     */
    @Transactional
    public void receive(SepayWebhookRequest request, String source, Instant paidAt) {
        requireConfiguration();
        validate(request);
        if (events.existsById(request.id())) {
            return;
        }
        String reference = blankToNull(request.referenceCode());
        if (reference != null && events.existsByGatewayAndReferenceCode(request.gateway(), reference)) {
            return;
        }
        Instant now = clock.now();
        Instant paidTime = paidAt == null || paidAt.isAfter(now) ? now : paidAt;
        String code = findCode(request.content());
        SepayWebhookEvent event = new SepayWebhookEvent();
        event.setTransactionId(request.id());
        event.setGateway(request.gateway());
        event.setAccountNumber(request.accountNumber());
        event.setSubAccount(blankToNull(request.subAccount()));
        event.setPaymentCode(code);
        event.setTransferType(request.transferType());
        event.setAmount(request.transferAmount());
        event.setReferenceCode(reference);
        event.setResult("REVIEW");
        event.setReviewReason("UNMATCHED_ACCOUNT_OR_CODE");
        event.setReceivedAt(now);
        event.setSource(source);
        event.setContent(request.content() == null ? null : request.content().strip());
        event = events.saveAndFlush(event);

        if (!"BIDV".equalsIgnoreCase(request.gateway())
                || !"in".equalsIgnoreCase(request.transferType())
                || !bankAccount.trim().equals(request.accountNumber())
                || !virtualAccount.trim().equals(request.subAccount())
                || code == null) {
            return;
        }
        UUID orderId = payments.findOrderIdByPaymentNumber(code).orElse(null);
        if (orderId == null) {
            event.setReviewReason("PAYMENT_NOT_FOUND");
            return;
        }
        Order order = orders.findForUpdate(orderId).orElse(null);
        Payment payment = payments.findByPaymentNumberForUpdate(code).orElse(null);
        if (order == null || payment == null) {
            event.setReviewReason("PAYMENT_NOT_FOUND");
            return;
        }
        event.setPaymentId(payment.getId());
        if (reservationExpired(order, paidTime)) {
            event.setReviewReason("RESERVATION_EXPIRED");
            return;
        }
        event.setReviewReason("PAYMENT_STATE_OR_AMOUNT_MISMATCH");
        if (!"SEPAY".equals(payment.getProvider()) || !"BANK_TRANSFER".equals(payment.getPaymentMethod())
                || !"PENDING".equals(payment.getStatus())
                || (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PROCESSING)
                || payments.existsByOrderIdAndStatus(orderId, "SUCCESS")
                || request.transferAmount().compareTo(payment.getAmount()) != 0
                || order.getTotalAmount().compareTo(payment.getAmount()) != 0) {
            return;
        }
        payment.setStatus("SUCCESS");
        payment.setProviderTransactionId(request.id().toString());
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);
        // Thanh toán xong là đơn hoàn tất; trạng thái giao hàng nằm ở từng package.
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(now);
        order.setUpdatedAt(now);
        if (order.getCouponId() != null && order.getCouponRedeemedAt() == null) {
            order.setCouponRedeemedAt(now);
            order.setUpdatedAt(now);
        }
        if (order.getShippingCouponId() != null && order.getShippingCouponRedeemedAt() == null) {
            order.setShippingCouponRedeemedAt(now);
            order.setUpdatedAt(now);
        }
        event.setResult("MATCHED");
        event.setReviewReason(null);
        outbox.record("payment.succeeded", "PAYMENT", payment.getId(), Map.of(
                "orderId", orderId,
                "customerId", order.getCustomerId(),
                "paymentNumber", payment.getPaymentNumber(),
                "amount", payment.getAmount(),
                "paidAt", now));
    }

    public void checkWebhookConfiguration() {
        requireConfiguration();
    }

    private void requireOwner(Order order, UUID customerId) {
        if (!order.getCustomerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private boolean reservationExpired(Order order, Instant now) {
        return order.getReservationReleasedAt() != null
                || (order.getReservationExpiresAt() != null && !now.isBefore(order.getReservationExpiresAt()));
    }

    private void requireConfiguration() {
        if (bankAccount == null || bankAccount.isBlank()
                || virtualAccount == null || virtualAccount.isBlank()
                || webhookSecret == null || webhookSecret.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
    }

    private BankPaymentResponse response(Payment payment, boolean payable) {
        String qrUrl = null;
        if (payable && "PENDING".equals(payment.getStatus())) {
            qrUrl = UriComponentsBuilder.fromUriString("https://vietqr.app/img")
                    .queryParam("acc", virtualAccount.trim())
                    .queryParam("bank", "BIDV")
                    .queryParam("amount", payment.getAmount().toPlainString())
                    .queryParam("des", payment.getPaymentNumber())
                    .build().encode().toUriString();
        }
        return new BankPaymentResponse(payment.getId(), payment.getOrderId(),
                payment.getPaymentNumber(), payment.getAmount(), payment.getCurrency(),
                payment.getStatus(), "BIDV", virtualAccount.trim(), qrUrl);
    }

    private void validate(SepayWebhookRequest request) {
        if (request == null || request.id() == null || request.id() <= 0
                || request.gateway() == null || request.gateway().isBlank() || request.gateway().length() > 100
                || request.accountNumber() == null || request.accountNumber().isBlank() || request.accountNumber().length() > 100
                || request.subAccount() != null && request.subAccount().length() > 100
                || request.referenceCode() != null && request.referenceCode().length() > 100
                || request.transferType() == null || request.transferType().length() > 10
                || request.transferAmount() == null || request.transferAmount().signum() <= 0
                || request.transferAmount().scale() > 0 || request.transferAmount().precision() > 16) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private String findCode(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = PAYMENT_CODE.matcher(content.toUpperCase(Locale.ROOT));
        if (!matcher.find()) {
            return null;
        }
        String code = matcher.group();
        return matcher.find() ? null : code;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
