package com.se203730.mvc.generateuibe.features.customer.dto.response;

import com.se203730.mvc.generateuibe.features.customer.entity.PackageRefund;

import java.time.Instant;
import java.util.UUID;

public record RefundRequestResponse(UUID id, UUID requestGroupId, UUID orderId, UUID packageId,
                                    String packageName, String status, String reason, Instant createdAt) {
    public static RefundRequestResponse from(PackageRefund refund, String packageName) {
        return new RefundRequestResponse(refund.getId(), refund.getRequestGroupId(), refund.getOrderId(),
                refund.getOrderPackageId(), packageName, refund.getStatus(), refund.getReason(), refund.getCreatedAt());
    }
}
