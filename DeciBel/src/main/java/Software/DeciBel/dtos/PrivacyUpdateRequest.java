package software.decibel.dtos;

import jakarta.validation.constraints.NotNull;

public record PrivacyUpdateRequest(
        @NotNull Boolean isPrivate,
        @NotNull Boolean showHistory
) {
}
