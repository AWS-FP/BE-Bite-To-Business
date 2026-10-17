package com.se203730.mvc.generateuibe.common.security;

import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.security.access.AccessDeniedException;

/**
 * Mang theo ErrorCode khi Security Filter từ chối tài khoản để response giữ được đúng lý do, ví dụ tài khoản bị cấm.
 */
@Getter
public class AccountAccessDeniedException extends AccessDeniedException {
    private final ErrorCode errorCode;

    public AccountAccessDeniedException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
