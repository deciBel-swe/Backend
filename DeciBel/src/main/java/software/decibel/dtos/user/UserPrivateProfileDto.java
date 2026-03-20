package software.decibel.dtos.user;

public record UserPrivateProfileDto(UserPublicProfileDto profile,
        boolean isPrivate,
        boolean showHistory,
        boolean emailVerified) {

}
