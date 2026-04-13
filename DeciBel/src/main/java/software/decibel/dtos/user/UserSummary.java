package software.decibel.dtos.user;

public record UserSummary(
        Long id,
        String username,
        String displayName,
        String avatarUrl) {
}
