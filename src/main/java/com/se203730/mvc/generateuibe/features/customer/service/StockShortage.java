package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.ErrorResponse;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng thiếu hàng trong errors[] của QUOTE_STOCK_UNAVAILABLE.
 * field = productId, message = "available=…;requested=…" để FE tự hiển thị tên sản phẩm theo ngôn ngữ.
 */
final class StockShortage {

    static final String CODE = "INSUFFICIENT_STOCK";

    private StockShortage() {
    }

    static ErrorResponse.ValidationError of(UUID productId, BigDecimal available, BigDecimal requested) {
        BigDecimal safeAvailable = available == null || available.signum() < 0 ? BigDecimal.ZERO : available;
        return new ErrorResponse.ValidationError(
                productId.toString(),
                CODE,
                "available=" + safeAvailable.stripTrailingZeros().toPlainString()
                        + ";requested=" + requested.stripTrailingZeros().toPlainString());
    }
}
