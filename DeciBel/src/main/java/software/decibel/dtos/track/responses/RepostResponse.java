package software.decibel.dtos.track.responses;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RepostResponse(
        @NotBlank
        String message,

        @NotNull
        Boolean isReposted
) {
}
