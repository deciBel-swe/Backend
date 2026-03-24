package software.decibel.exceptions.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL) // So that null fields are not included
@Builder
public record ApiErrorResponse(
    LocalDateTime timestamp, // when the error occurred
    int status,
    String error, // short error category (ex. Validation Failed)
    String message, // longer human-readable explanation
    String path, // which endpoint caused the error
    List<String> errors // field-level validation errors (can be null)
    ) {}
