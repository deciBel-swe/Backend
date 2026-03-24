package software.decibel.dtos.track;

public record TrackUploadResponse(
    Long id, String title, String trackUrl, String coverUrl, Integer durationSeconds) {}
