package software.decibel.dtos.playlist;

public record OwnerDto(
        Long userId,
        String username,
        String displayName,
        String avatarUrl) {

}
