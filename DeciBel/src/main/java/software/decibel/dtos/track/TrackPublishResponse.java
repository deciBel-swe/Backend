package software.decibel.dtos.track;

import java.time.LocalDateTime;

public record TrackPublishResponse(Long id, String slug, LocalDateTime publishedAt) {}
