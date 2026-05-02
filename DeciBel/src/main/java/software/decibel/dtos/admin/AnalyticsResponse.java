package software.decibel.dtos.admin;

public record AnalyticsResponse(
        Long totalUsers,
        Long totalTracks,
        Long totalPlays,
        Double playThroughRate,
        Long totalStorageUsedBytes,
        Long totalStorageCapacityBytes) {
}
