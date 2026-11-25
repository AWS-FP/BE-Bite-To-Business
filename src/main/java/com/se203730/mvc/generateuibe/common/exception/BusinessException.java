package com.se203730.mvc.generateuibe.common.exception;

import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * Lỗi nghiệp vụ do service chủ động ném ra. GlobalExceptionHandler chuyển ErrorCode thành JSON phản hồi.
 * details (tuỳ chọn) đi vào trường errors[] của phản hồi, ví dụ từng sản phẩm thiếu hàng.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ErrorResponse.ValidationError> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, (Throwable) null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage(), cause);
        this.errorCode = errorCode;
        this.details = List.of();
    }

    public BusinessException(ErrorCode errorCode, List<ErrorResponse.ValidationError> details) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage());
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }
}
