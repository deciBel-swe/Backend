package software.decibel.dtos.track.responses;

public record TrackUploadResponse(
    Long id,
    String title,
    String trackUrl,
    String trackPreviewUrl,
    String coverUrl,
    Integer durationSeconds) {}
