package software.decibel.dtos.auth.google;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleTokenInfoResponse(
        @JsonProperty("sub")
        String subject,

        String email,

        @JsonProperty("email_verified")
        String emailVerified,

        String name,

        String picture,

        @JsonProperty("aud")
        String audience,

        @JsonProperty("iss")
        String issuer,

        @JsonProperty("exp")
        String expiresAtEpochSeconds
) {
}
