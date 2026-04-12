package software.decibel.dtos.notifications;

public record NotificationSettingsDto(
        boolean notifyOnFollow,
        boolean notifyOnLike,
        boolean notifyOnRepost,
        boolean notifyOnComment,
        boolean notifyOnDM
        ) {

}
