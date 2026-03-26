package software.decibel.dtos.user;

import lombok.Builder;
/**
 * DTO for user information in blocked lists.
 */
@Builder
public record BlockedUserDto(
    Long id,
    String username,
    String displayName,
    String avatarUrl
) {}
