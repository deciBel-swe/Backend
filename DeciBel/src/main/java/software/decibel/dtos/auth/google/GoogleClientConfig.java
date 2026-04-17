package software.decibel.dtos.auth.google;

public record GoogleClientConfig(
        String name,
        String clientId,
        String clientSecret,
        String redirectUri) {
}
