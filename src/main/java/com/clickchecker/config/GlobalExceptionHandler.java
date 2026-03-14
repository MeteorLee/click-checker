package com.clickchecker.config;

import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final List<String> SCANNER_PATH_PATTERNS = List.of(
            "/.env",
            "/wp-admin",
            "/wordpress/",
            "/wp-includes/",
            "/.git/",
            "wlwmanifest.xml",
            "/v1/models",
            "/v1/embeddings",
            "/v1/completions",
            "/admin/assets/",
            "/favicon.ico",
            "/robots.txt",
            "/sitemap.xml",
            "/feed/",
            ".php",
            ".bak",
            ".old",
            ".save",
            ".dist",
            ".sample",
            "~"
    );

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        if (status.is5xxServerError()) {
            Sentry.captureException(ex);
        }
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), ex.getReason(), request.getRequestURI(), List.of())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                                HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();

        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), "Validation failed", request.getRequestURI(), details)
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex,
                                                                            HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), "Validation failed", request.getRequestURI(), details)
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex,
                                                                                   HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = ex.getName() + ": invalid value";
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI(), List.of())
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), "Malformed JSON request", request.getRequestURI(), List.of())
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), "Validation failed", request.getRequestURI(), details)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        if (!(ex instanceof NoResourceFoundException)
                || shouldCaptureNoResourceFound(request)) {
            Sentry.captureException(ex);
        }
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), "Internal server error", request.getRequestURI(), List.of())
        );
    }

    private boolean shouldCaptureNoResourceFound(HttpServletRequest request) {
        String path = normalizePath(request.getRequestURI());
        return SCANNER_PATH_PATTERNS.stream().noneMatch(path::contains);
    }

    private String normalizePath(String path) {
        return path == null
                ? ""
                : path.toLowerCase()
                        .replaceAll("/{2,}", "/");
    }

    private String formatFieldError(FieldError e) {
        return e.getField() + ": " + e.getDefaultMessage();
    }

    public record ErrorResponse(
            String timestamp,
            int status,
            String error,
            String message,
            String path,
            List<String> details
    ) {
        static ErrorResponse of(int status, String error, String message, String path, List<String> details) {
            return new ErrorResponse(
                    OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    status,
                    error,
                    message,
                    path,
                    details
            );
        }
    }
}
