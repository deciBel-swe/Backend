package software.decibel.dtos.moderation;

import jakarta.validation.constraints.NotBlank;

public record ReportRequest(
        @NotBlank
        String reason,

        String description) {
}
