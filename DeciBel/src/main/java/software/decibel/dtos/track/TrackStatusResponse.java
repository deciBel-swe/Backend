package software.decibel.dtos.track;

import software.decibel.enums.TrackState;

public record TrackStatusResponse(TrackState trackState, Long trackId) {}
