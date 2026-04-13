package software.decibel.dtos.track;

import software.decibel.dtos.user.UserSummary;

public record TrackSummaryDTO(
    Long id,
    String title,
    String trackSlug,
    String coverUrl,
    String trackUrl,
    UserSummary artist,
    int playCount,
    int likeCount,
    int repostCount,
    int commentCount,
    boolean isLiked,
    boolean isReposted,
    String secretToken
    // ENUM ACCESS - to be added when logic is implemented
    ) {}
