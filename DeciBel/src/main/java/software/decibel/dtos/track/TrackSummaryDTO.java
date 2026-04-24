package software.decibel.dtos.track;

import software.decibel.dtos.user.UserSummaryDTO;
import software.decibel.enums.TrackAccess;

public record TrackSummaryDTO(
        Long id,
        String title,
        String trackSlug,
        String coverUrl,
        String trackUrl,
        String trackPreviewUrl,
        UserSummaryDTO artist,
        int playCount,
        int likeCount,
        int repostCount,
        int commentCount,
        boolean isLiked,
        boolean isReposted,
        String secretToken,
        TrackAccess access) {}
