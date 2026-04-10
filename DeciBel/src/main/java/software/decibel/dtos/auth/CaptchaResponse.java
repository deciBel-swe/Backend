package software.decibel.dtos.auth;

public record CaptchaResponse(
        boolean success,
        Double score,
        String action,
        String hostname) {

}
