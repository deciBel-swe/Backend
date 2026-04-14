package software.decibel.dtos.notifications;

import software.decibel.enums.NotificationType;
import software.decibel.dtos.user.UserSummary;

import java.time.LocalDateTime;

public record NotificationDto(Long id,
        NotificationType type,
        UserSummary user,
        NotificationResourceDto resource,
        boolean isRead,
        LocalDateTime createdAt) {

}
