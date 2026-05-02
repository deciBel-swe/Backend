package software.decibel.dtos.discovery;

import java.time.LocalDateTime;

import software.decibel.dtos.user.UserSummaryDTO;

public record FeedItemDto(
        Long id,
        String type,
        ResourceItemDto resource,
        UserSummaryDTO repostedBy,
        LocalDateTime createdAt) {

}
