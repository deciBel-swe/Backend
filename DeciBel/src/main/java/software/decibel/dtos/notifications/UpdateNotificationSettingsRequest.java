package software.decibel.dtos.notifications;

public record UpdateNotificationSettingsRequest(
        Boolean notifyOnFollow,
        Boolean notifyOnLike,
        Boolean notifyOnRepost,
        Boolean notifyOnComment,
        Boolean notifyOnDM
        ) {

}
