package software.decibel.dtos.track.responses;

import java.time.LocalDateTime;

public record TrackPublishResponse(Long id, String slug, LocalDateTime publishedAt) {}
