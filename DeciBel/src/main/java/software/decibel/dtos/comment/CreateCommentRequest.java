package software.decibel.dtos.comment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest(
    @NotNull(message = "Comment must not be null/blank")
        @NotBlank(message = "Comment must not be null/blank")
        String body,
    @Min(value = 0, message = "Comment timestamp cannot be below 0") Integer timestampSeconds) {}
