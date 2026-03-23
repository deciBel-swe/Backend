package software.decibel.dtos.auth;

public record AuthTokenRotationResult(
        RefreshTokenResponse response,
        String refreshToken,
        long refreshTokenExpiresIn) {

}
