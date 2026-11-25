package com.se203730.mvc.generateuibe.features.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin xử lý một khoản SePay chưa khớp đơn.
 * REFUNDED: đã tự chuyển trả, bắt buộc refundReference (mã giao dịch hoàn).
 * RESTORED: khôi phục đơn và coi như đã thanh toán (chỉ khi đúng số tiền và còn hàng).
 * IGNORED: khoản không liên quan đơn nào, bắt buộc note.
 */
public record ResolvePaymentReviewRequest(
        @NotNull
        Action action,
        @Size(max = 100)
        String refundReference,
        @Size(max = 2000)
        String note
) {
    public enum Action {
        REFUNDED, RESTORED, IGNORED
    }

    public ResolvePaymentReviewRequest {
        refundReference = refundReference == null || refundReference.isBlank() ? null : refundReference.strip();
        note = note == null || note.isBlank() ? null : note.strip();
    }
}
