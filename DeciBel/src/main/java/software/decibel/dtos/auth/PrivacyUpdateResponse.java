package software.decibel.dtos.auth;

public record PrivacyUpdateResponse(
        boolean isPrivate,
        boolean showHistory
        ) {

}
