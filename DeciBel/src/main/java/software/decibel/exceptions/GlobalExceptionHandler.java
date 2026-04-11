package software.decibel.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import software.decibel.exceptions.custom.*;
import software.decibel.exceptions.response.ApiErrorResponse;

@RestControllerAdvice
@Slf4j // provides log.error(), log.warn(), etc.
public class GlobalExceptionHandler {

  // -- 204 -- Not an error

  // for when no content returned
  // because no results for this station
  @ExceptionHandler(NoStationResultsException.class)
  public ResponseEntity<ApiErrorResponse> handleNoStationResultsException(
      NoStationResultsException ex, HttpServletRequest request) {

    ApiErrorResponse error =
        ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NO_CONTENT.value())
            .error("No Results")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

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

    // ── 403 — Unauthorized Violation
    @ExceptionHandler(UnauthorizedActionException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorizedActionException(
            UnauthorizedActionException ex, HttpServletRequest request) {

        ApiErrorResponse error
                = ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.FORBIDDEN.value())
                        .error("Forbidden")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ── 400 — Business Rule Violations
    // Called when a comment's timestamp (the time of a track that's being commented on ) is greater
    // than the comment's duration (should be impossible)
    @ExceptionHandler(InvalidTimestampException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTimestampException(
            InvalidTimestampException ex, HttpServletRequest request) {

        ApiErrorResponse error
                = ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Invalid Timestamp")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build();

        return ResponseEntity.badRequest().body(error);
    }

    // Called when trying to reply to another reply (according to the docs replies are one level max)
    @ExceptionHandler(ReplyToReplyNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleReplyToReplyNotAllowedException(
            ReplyToReplyNotAllowedException ex, HttpServletRequest request) {

        ApiErrorResponse error
                = ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Reply Not Allowed")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build();

        return ResponseEntity.badRequest().body(error);
    }

    //called when trying to do invalid ordering on playlists
    @ExceptionHandler(InvalidPlaylistOperationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPlaylistOperation(InvalidPlaylistOperationException ex) {

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", LocalDateTime.now());
        errorDetails.put("status", HttpStatus.BAD_REQUEST.value()); // 400
        errorDetails.put("error", "Bad Request");
        errorDetails.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    //Track already in playlist
    @ExceptionHandler(TrackAlreadyInPlaylistException.class)
    public ResponseEntity<Map<String, Object>> handleTrackAlreadyInPlaylist(TrackAlreadyInPlaylistException ex) {

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", LocalDateTime.now());
        errorDetails.put("status", HttpStatus.CONFLICT.value()); // 409 Conflict
        errorDetails.put("error", "Conflict");
        errorDetails.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDetails);
    }

    // Called when trying to access a playlist that the user doesn't have permission to view
    @ExceptionHandler(PlaylistAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handlePlaylistAccessDenied(PlaylistAccessDeniedException ex, HttpServletRequest request) {

        ApiErrorResponse error
                = ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.FORBIDDEN.value())
                        .error("Forbidden")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // Called when trying to publish an already published track
    @ExceptionHandler(TrackAlreadyPublishedException.class)
    public ResponseEntity<ApiErrorResponse> handleTrackAlreadyPublishedException(
            TrackAlreadyPublishedException ex, HttpServletRequest request) {

        ApiErrorResponse error
                = ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.CONFLICT.value())
                        .error("Track already published")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build();

        return ResponseEntity.badRequest().body(error);
    }

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

    //Invalid Google token provided during authentication
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
    // ── 404 — Endpoint Not Found 

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleEndpointNotFoundException(
            Exception ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Endpoint Not Found")
                // We use request.getMethod() and request.getRequestURI() because they work for both exception types
                .message(String.format("The endpoint %s %s does not exist.", request.getMethod(), request.getRequestURI()))
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
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

    @ExceptionHandler(CooldownActiveException.class)
    public ResponseEntity<ApiErrorResponse> handleCooldownException(
            CooldownActiveException ex, HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }
}
