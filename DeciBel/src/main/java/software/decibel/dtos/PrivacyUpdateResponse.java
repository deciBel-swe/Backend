package software.decibel.dtos;

public record PrivacyUpdateResponse(
        boolean isPrivate,
        boolean showHistory
        ) {

}
