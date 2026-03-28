package software.decibel.exceptions;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import software.decibel.exceptions.custom.AudioDurationReadingException;
import software.decibel.exceptions.custom.AzureFileStorageException;
import software.decibel.exceptions.custom.CaptchaValidationException;
import software.decibel.exceptions.custom.DuplicateResourceException;
import software.decibel.exceptions.custom.ExternalAuthConfigurationException;
import software.decibel.exceptions.custom.InvalidGoogleTokenException;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.TierException;
import software.decibel.exceptions.response.ApiErrorResponse;

@RestControllerAdvice
@Slf4j // provides log.error(), log.warn(), etc.
public class GlobalExceptionHandler {

    // ── 400 — DTO Validation (@Valid failed)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        // Collect all field-level error messages from the DTO
        List<String> validationErrors = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage)
                .toList();

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("One or more fields are invalid.")
                .path(request.getRequestURI())
                .errors(validationErrors) // the list of messages
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    // ── 400 — Database Constraint Violation
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.error("Database constraint violation at {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Database Error")
                .message(
                        "A database constraint was violated. Check for duplicate values or missing required fields.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    // ── 400 — Captcha Validation Failure
    @ExceptionHandler(CaptchaValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleCaptchaValidationException(
            CaptchaValidationException ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Captcha Validation Failed")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(error);
    }
    // ── 400 — Tier Business Rule Violation

    @ExceptionHandler(TierException.class)
    public ResponseEntity<ApiErrorResponse> handleTierException(
            TierException ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Tier Update Error")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    // ── 404 — Resource Not Found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // ── 409 — Duplicate Entity
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateException(
            DuplicateResourceException ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // ── 400 — Business Rule Violations
    // -- 500 --internal service error
    @ExceptionHandler(AudioDurationReadingException.class)
    public ResponseEntity<ApiErrorResponse> handleAudioDurationReadingException(
            AudioDurationReadingException ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("File Reading Error")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.internalServerError().body(error);
    }

    // -- 503 -- Service Unavailable Violations
    // For Azure Microsoft related errors
    @ExceptionHandler(AzureFileStorageException.class)
    public ResponseEntity<ApiErrorResponse> handleAzureFileStorageException(
            AzureFileStorageException ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("File Storage Error")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    // ── 500 — Catch All Safety Net

    @ExceptionHandler(ExternalAuthConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalAuthConfigurationException(
            ExternalAuthConfigurationException ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("External Authentication Configuration Error")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // ── 401 — Invalid Google Token
    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidGoogleTokenException(
            InvalidGoogleTokenException ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // ── 500 — Catch All Safety Net
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        // Always log unexpected exceptions with full stack trace
        log.error("Unexpected error at {}", request.getRequestURI(), ex);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
