package software.decibel.dtos.track.responses;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LikeResponse(
        @NotBlank
        String message,

        @NotNull
        Boolean isLiked
) {
}
