package com.se203730.mvc.generateuibe.features.customer.dto.response;

import com.se203730.mvc.generateuibe.features.customer.entity.PackageRefund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundRequestResponse(UUID id, UUID requestGroupId, UUID orderId, UUID packageId,
                                    String packageName, String status, String reason, BigDecimal refundAmount,
                                    String reviewNote, String transferReference, Instant createdAt,
                                    Instant reviewedAt, Instant refundedAt) {
    public static RefundRequestResponse from(PackageRefund refund, UUID orderId, String packageName) {
        return new RefundRequestResponse(refund.getId(), refund.getRequestGroupId(), orderId,
                refund.getOrderPackageId(), packageName, refund.getStatus(), refund.getReason(),
                refund.getRefundAmount(), refund.getReviewNote(), refund.getTransferReference(),
                refund.getCreatedAt(), refund.getReviewedAt(), refund.getRefundedAt());
    }
}
