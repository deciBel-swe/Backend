package software.decibel.dtos.admin;

import lombok.Builder;

@Builder
public record LoginAdminResponse(
    String accessToken,
    Long expiresIn,
    AdminUserResponse adminUser
) {}
