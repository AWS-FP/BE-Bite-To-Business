package com.se203730.mvc.generateuibe.common.exception;

import lombok.Getter;

import java.util.Objects;

/**
 * Lỗi nghiệp vụ do service chủ động ném ra. GlobalExceptionHandler chuyển ErrorCode thành JSON phản hồi.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage(), cause);
        this.errorCode = errorCode;
    }
}
