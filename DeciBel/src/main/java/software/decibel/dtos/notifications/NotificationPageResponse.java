package software.decibel.dtos.notifications;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationDto> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast) {

}
