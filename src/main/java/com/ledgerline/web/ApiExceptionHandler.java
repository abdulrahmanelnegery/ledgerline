package com.ledgerline.web;

import com.ledgerline.domain.InvalidJournalEntryException;
import com.ledgerline.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates domain and validation failures into a stable error envelope:
 * {@code {"error": {"code", "message", "details"}}}.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    record ApiError(ErrorBody error) {
        record ErrorBody(String code, String message, List<String> details) {
        }

        static ResponseEntity<ApiError> of(HttpStatus status, String code, String message, List<String> details) {
            return ResponseEntity.status(status).body(new ApiError(new ErrorBody(code, message, details)));
        }
    }

    @ExceptionHandler(InvalidJournalEntryException.class)
    ResponseEntity<ApiError> onUnbalanced(InvalidJournalEntryException ex) {
        return ApiError.of(HttpStatus.UNPROCESSABLE_ENTITY, "entry_not_balanced", ex.getMessage(), List.of());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> onNotFound(NotFoundException ex) {
        return ApiError.of(HttpStatus.NOT_FOUND, "not_found", ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onInvalidBody(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();
        return ApiError.of(HttpStatus.BAD_REQUEST, "invalid_request", "request body failed validation", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException ex) {
        return ApiError.of(HttpStatus.BAD_REQUEST, "invalid_request", ex.getMessage(), List.of());
    }
}
