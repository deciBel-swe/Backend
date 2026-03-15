package software.decibel.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import software.decibel.exceptions.custom.AudioDurationReadingException;
import software.decibel.exceptions.custom.DuplicateResourceException;
import software.decibel.exceptions.custom.FileStorageException;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.response.ApiErrorResponse;

@RestControllerAdvice
@Slf4j // provides log.error(), log.warn(), etc.
public class GlobalExceptionHandler {

  // ── 400 — DTO Validation (@Valid failed)

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {

    // Collect all field-level error messages from the DTO
    List<String> validationErrors =
        ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).toList();

    ApiErrorResponse error =
        ApiErrorResponse.builder()
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

    ApiErrorResponse error =
        ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Database Error")
            .message(
                "A database constraint was violated. Check for duplicate values or missing required fields.")
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.badRequest().body(error);
  }

  // ── 404 — Resource Not Found

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNotFoundException(
      ResourceNotFoundException ex, HttpServletRequest request) {

    ApiErrorResponse error =
        ApiErrorResponse.builder()
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

    ApiErrorResponse error =
        ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Conflict")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  // ── 400 — Business Rule Violations

  @ExceptionHandler(AudioDurationReadingException.class)
  public ResponseEntity<ApiErrorResponse> handleAudioDurationReadingException(
      AudioDurationReadingException ex, HttpServletRequest request) {

    ApiErrorResponse error =
        ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Business Rule Violation")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.badRequest().body(error);
  }

  @ExceptionHandler(FileStorageException.class)
  public ResponseEntity<ApiErrorResponse> handleFileStorageException(
      FileStorageException ex, HttpServletRequest request) {

    ApiErrorResponse error =
        ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Business Rule Violation")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.badRequest().body(error);
  }

  // ── 500 — Catch All Safety Net
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleGenericException(
      Exception ex, HttpServletRequest request) {

    // Always log unexpected exceptions with full stack trace
    log.error("Unexpected error at {}", request.getRequestURI(), ex);

    ApiErrorResponse error =
        ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred. Please try again later.")
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
