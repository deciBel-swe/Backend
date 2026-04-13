package software.decibel.dtos.track;

import software.decibel.enums.TrackState;

public record TrackStatusResponse(
        TrackState trackState,
        Long trackId,
        Integer progressPercentage,
        String stepName,
        String errorMessage,
        TrackResponse trackResponse) {

// Constructor for basic state updates (e.g., UPLOADING just started)
    public TrackStatusResponse(TrackState trackState, Long trackId) {
        this(trackState, trackId, null, null, null, null); // Added 6th argument
    }

    // Constructor for progress updates
    public TrackStatusResponse(TrackState trackState, Long trackId, Integer progressPercentage, String stepName) {
        this(trackState, trackId, progressPercentage, stepName, null, null); // Added 6th argument
    }

    // Optional constructor for errors
    public TrackStatusResponse(TrackState trackState, Long trackId, String errorMessage) {
        this(trackState, trackId, null, null, errorMessage, null);
    }
}
