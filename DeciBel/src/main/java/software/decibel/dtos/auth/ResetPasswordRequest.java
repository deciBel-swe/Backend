package software.decibel.dtos.auth;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for reset password requests.
 *
 * @param token the secret token provided in the password reset link.
 * @param newPassword the user's new password. Must be 8-100 characters and
 * include at least one uppercase letter, one lowercase letter, one number, and
 * one special character.
 */
public record ResetPasswordRequest(
        @NotBlank
        String token,
        @NotBlank
        @Size(min = 2, max = 200)
        String newPassword
        ) {

}
