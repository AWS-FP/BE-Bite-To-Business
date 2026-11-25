package com.se203730.mvc.generateuibe.features.admin.dto.response;

import com.se203730.mvc.generateuibe.features.customer.repository.SepayWebhookEventRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Một khoản tiền SePay chưa khớp đơn trên trang Admin "Thanh toán cần xử lý". */
public record PaymentReviewResponse(
        Long transactionId,
        String source,
        String gateway,
        String paymentCode,
        BigDecimal amount,
        String referenceCode,
        String content,
        String reviewReason,
        Instant receivedAt,
        UUID paymentId,
        String paymentStatus,
        BigDecimal paymentAmount,
        UUID orderId,
        String orderNumber,
        String orderStatus,
        BigDecimal orderTotal,
        String contactName,
        String contactEmail,
        String contactPhone,
        boolean restorable,
        String resolution,
        String resolutionNote,
        String refundReference,
        Instant resolvedAt,
        String resolvedByName,
        Instant customerNotifiedAt
) {
    public static PaymentReviewResponse from(SepayWebhookEventRepository.ReviewRow row) {
        // Gợi ý cho FE; service vẫn kiểm tra lại đầy đủ khi Admin bấm "Khôi phục đơn".
        boolean restorable = row.getResolution() == null && row.getOrderId() != null
                && row.getPaymentAmount() != null && row.getAmount().compareTo(row.getPaymentAmount()) == 0
                && row.getOrderTotal() != null && row.getAmount().compareTo(row.getOrderTotal()) == 0
                && !"SUCCESS".equals(row.getPaymentStatus())
                && ("CANCELLED".equals(row.getOrderStatus()) || "CONFIRMED".equals(row.getOrderStatus()));
        return new PaymentReviewResponse(row.getTransactionId(), row.getSource(), row.getGateway(),
                row.getPaymentCode(), row.getAmount(), row.getReferenceCode(), row.getContent(),
                row.getReviewReason(), row.getReceivedAt(), row.getPaymentId(), row.getPaymentStatus(),
                row.getPaymentAmount(), row.getOrderId(), row.getOrderNumber(), row.getOrderStatus(),
                row.getOrderTotal(), row.getContactName(), row.getContactEmail(), row.getContactPhone(),
                restorable, row.getResolution(), row.getResolutionNote(), row.getRefundReference(),
                row.getResolvedAt(), row.getResolvedByName(), row.getCustomerNotifiedAt());
    }
}
