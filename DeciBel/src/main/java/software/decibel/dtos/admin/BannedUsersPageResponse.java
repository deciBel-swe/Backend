package software.decibel.dtos.admin;

import java.util.List;

public record BannedUsersPageResponse(
        List<BannedUserResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast,
        long totalBannedUsers) {
}
