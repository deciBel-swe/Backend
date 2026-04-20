package software.decibel.dtos.admin;

public record BannedUserResponse(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        Boolean isBanned) {
}
