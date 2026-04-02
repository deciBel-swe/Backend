package software.decibel.dtos.user;

public record UpdateProfileResponse(
        UserProfile profile,
        PrivacySettings privacySettings) {

}
