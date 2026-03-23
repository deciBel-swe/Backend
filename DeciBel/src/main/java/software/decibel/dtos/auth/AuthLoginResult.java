package software.decibel.dtos.auth;

public record AuthLoginResult(
        LoginLocalResponse response,
        String refreshToken,
        long refreshTokenExpiresIn) {

}
