package software.decibel.dtos.notifications;

import software.decibel.enums.ResourceType;

public record NotificationResourceDto(ResourceType resourceType,
        Long resourceId) {

}
