package software.decibel.dtos.user;

import lombok.Builder;

/**
 * DTO for user information in follow lists.
 */
@Builder(toBuilder = true)
public record UserFollowDto(
    Long id,
    String username,
    String displayName,
    String avatarUrl,
    // Indicates if the current viewer is following this user
    boolean isFollowing
) {}
