package com.se203730.mvc.generateuibe.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Bắt lỗi phát sinh trong Spring MVC và trả JSON thống nhất thay vì để thông tin lỗi nội bộ lọt ra frontend.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(
            BusinessException ex,
            WebRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("Business operation failed at {}", requestPath(request), ex);
        }
        return respond(code, List.of(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException ex,
            WebRequest request) {
        // Lỗi dữ liệu trả ra là lỗi backend (500); lỗi dữ liệu người dùng nhập mới được trả thành lỗi validation (400).
        boolean invalidReturnValue = ex.getConstraintViolations().stream()
                .anyMatch(violation -> StreamSupport
                        .stream(violation.getPropertyPath().spliterator(), false)
                        .anyMatch(node -> node.getKind() == ElementKind.RETURN_VALUE));
        if (invalidReturnValue) {
            log.error("Response validation failed at {}", requestPath(request), ex);
            return respond(ErrorCode.INTERNAL_SERVER_ERROR, List.of(), request);
        }

        List<ErrorResponse.ValidationError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.ValidationError(
                        violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType()
                                .getSimpleName(),
                        violation.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, errors, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataConflict(
            DataIntegrityViolationException ex,
            WebRequest request) {
        // Hai request có thể cùng vượt qua bước kiểm tra trùng; unique index là chốt cuối.
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
                String constraintName = constraint.getConstraintName();
                if ("ux_users_phone".equals(constraintName)) {
                    return respond(ErrorCode.PHONE_ALREADY_EXISTS, List.of(), request);
                }
                if ("ux_users_email_ci".equals(constraintName)) {
                    return respond(ErrorCode.EMAIL_ALREADY_EXISTS, List.of(), request);
                }
            }
            cause = cause.getCause();
        }
        return respond(ErrorCode.CONFLICT, List.of(), request);
    }

    // Các handler này xử lý lỗi trong MVC; lỗi ở Security Filter do RestSecurityExceptionHandler xử lý.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(
            AuthenticationException ex,
            WebRequest request) {
        return respond(ErrorCode.UNAUTHORIZED, List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return respond(ErrorCode.ACCESS_DENIED, List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception ex, WebRequest request) {
        log.error("Unexpected error at {}", requestPath(request), ex);
        return respond(ErrorCode.INTERNAL_SERVER_ERROR, List.of(), request);
    }

    @Override
    protected @Nullable
    ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ErrorCode code = ErrorCode.fromHttpStatus(status);
        List<ErrorResponse.ValidationError> errors = List.of();

        if (ex instanceof MethodArgumentNotValidException validationException) {
            code = ErrorCode.VALIDATION_FAILED;
            errors = validationException.getBindingResult().getAllErrors().stream()
                    .map(this::validationError)
                    .toList();
        } else if (ex instanceof HandlerMethodValidationException validationException
                && !validationException.isForReturnValue()) {
            code = ErrorCode.VALIDATION_FAILED;
            errors = validationException.getParameterValidationResults().stream()
                    .flatMap(result -> result.getResolvableErrors().stream().map(error -> {
                        if (error instanceof ObjectError objectError) {
                            return validationError(objectError);
                        }
                        String name = result.getMethodParameter().getParameterName();
                        return new ErrorResponse.ValidationError(
                                name != null
                                        ? name
                                        : "arg" + result.getMethodParameter().getParameterIndex(),
                                "Invalid",
                                error.getDefaultMessage() != null
                                        ? error.getDefaultMessage()
                                        : "Invalid value.");
                    }))
                    .toList();
        }

        if (status.is5xxServerError()) {
            log.error("Request processing failed at {}", requestPath(request), ex);
        }

        // Giữ các header của Spring, chẳng hạn Allow cho lỗi 405.
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        ErrorResponse response = new ErrorResponse(status.value(), code.name(), code.getMessage(),
                requestPath(request), errors, Instant.now());
        return super.handleExceptionInternal(ex, response, responseHeaders, status, request);
    }

    private ResponseEntity<Object> respond(
            ErrorCode code,
            List<ErrorResponse.ValidationError> errors,
            WebRequest request) {
        ErrorResponse response = new ErrorResponse(code.getStatus().value(), code.name(),
                code.getMessage(),
                requestPath(request), errors, Instant.now());
        return ResponseEntity.status(code.getStatus()).contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    private ErrorResponse.ValidationError validationError(ObjectError error) {
        return new ErrorResponse.ValidationError(
                error instanceof FieldError fieldError
                        ? fieldError.getField()
                        : error.getObjectName(),
                error.getCode() != null ? error.getCode() : "Invalid",
                error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value.");
    }

    private static String requestPath(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : "";
    }
}
