package software.decibel.dtos.user;

public record UserSummaryDTO(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        boolean isFollowing,
        int followerCount,
        int trackCount) {}
