package software.decibel.dtos.admin;

import lombok.Builder;

@Builder
public record AdminUserResponse(
    Long id,
    String username,
    String avatarUrl
) {}
