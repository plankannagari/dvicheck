package com.dvicheck.backend.exception;

import com.dvicheck.backend.dto.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DvicheckException.class)
    public ResponseEntity<ApiErrorResponse> handleDvicheckException(DvicheckException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case "NOT_FOUND"           -> HttpStatus.NOT_FOUND;
            case "UNAUTHORIZED"        -> HttpStatus.UNAUTHORIZED;
            case "SERVICE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            // 402, not 403: the action is allowed, just not on the free plan
            case "PRO_REQUIRED"        -> HttpStatus.PAYMENT_REQUIRED;
            default                    -> HttpStatus.BAD_REQUEST;
        };
        return error(status, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalApi(ExternalApiException ex) {
        log.warn("External API failure: {}", ex.getMessage(), ex);
        return error(HttpStatus.BAD_GATEWAY, "EXTERNAL_AI_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have permission to do that.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }

    // Content-Type is set explicitly so Spring skips Accept-header negotiation. Without it, a
    // client sending `Accept: application/pdf` (e.g. GET /api/reports/monthly/pdf) makes this
    // JSON body "not acceptable" — Spring then falls back to its /error dispatch, which
    // Spring Security rejects, and the client sees a misleading 401 instead of 402/404/400.
    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code, message));
    }
}
