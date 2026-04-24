package software.decibel.dtos.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for an authenticated user password change.
 *
 * @param currentPassword the user's current password
 * @param newPassword the new password to set
 */
public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 100)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character."
        )
        String newPassword
) {
}
