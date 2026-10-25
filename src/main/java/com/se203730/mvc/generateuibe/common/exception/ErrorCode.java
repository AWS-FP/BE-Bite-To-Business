package com.se203730.mvc.generateuibe.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Danh sách mã lỗi nghiệp vụ thống nhất; mỗi mã đi kèm HTTP status và thông báo có thể trả cho frontend.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "The request is invalid."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Please check the submitted information."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to access this resource."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This HTTP method is not supported."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "The requested response format is not supported."),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current data."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The request exceeds the allowed size."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The request content type is not supported."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later."),
    EXTERNAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The external service is temporarily unavailable."),

    // Authentication
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Incorrect email or password."),
    ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "This account has been banned."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Please verify your email address."),
    ACCOUNT_NOT_ACTIVATED(HttpStatus.FORBIDDEN, "This account has not been activated."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "This email address is already registered."),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "This phone number is already registered."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "The user was not found."),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "The verification code is invalid."),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "The verification code has expired."),
    PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "Password confirmation does not match."),
    PASSWORD_POLICY_REJECTED(HttpStatus.BAD_REQUEST, "The password does not meet the password policy."),
    PASSWORD_LOGIN_REQUIRED(HttpStatus.FORBIDDEN, "Please sign in with your email and password."),
    REGISTRATION_ALREADY_REVIEWED(HttpStatus.CONFLICT, "This registration has already been reviewed."),
    REGISTRATION_ALREADY_ACTIVATED(HttpStatus.CONFLICT, "This account has already been activated."),
    REJECTION_NOTE_REQUIRED(HttpStatus.BAD_REQUEST, "A reason is required when rejecting a registration."),
    EMAIL_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "The email could not be sent. Please retry."),
    AUTH_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "The authentication service is not configured."),
    SETUP_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "The password setup session has expired.");

    private final HttpStatus status;
    private final String message;

    static ErrorCode fromHttpStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> UNAUTHORIZED;
            case 403 -> ACCESS_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> TOO_MANY_REQUESTS;
            case 503 -> EXTERNAL_SERVICE_UNAVAILABLE;
            default -> status.is5xxServerError() ? INTERNAL_SERVER_ERROR : INVALID_REQUEST;
        };
    }
}
