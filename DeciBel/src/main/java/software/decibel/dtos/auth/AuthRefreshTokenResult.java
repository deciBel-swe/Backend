package software.decibel.dtos.auth;

public record AuthRefreshTokenResult(
        String refreshToken,
        long refreshTokenExpiresIn) {

}
