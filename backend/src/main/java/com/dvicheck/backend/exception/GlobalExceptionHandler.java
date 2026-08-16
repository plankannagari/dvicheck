package com.dvicheck.backend.exception;

import com.dvicheck.backend.dto.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
            default                    -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalApi(ExternalApiException ex) {
        log.warn("External API failure: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrorResponse.of("EXTERNAL_AI_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("FORBIDDEN", "You don't have permission to do that."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "Something went wrong. Please try again."));
    }
}
