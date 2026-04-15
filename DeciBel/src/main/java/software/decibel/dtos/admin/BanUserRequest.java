package software.decibel.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BanUserRequest(
        @NotNull
        Boolean isBanned,

        @NotBlank
        String reason) {
}
