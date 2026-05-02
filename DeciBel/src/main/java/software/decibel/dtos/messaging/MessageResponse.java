package software.decibel.dtos.messaging;

import java.time.LocalDateTime;

import software.decibel.dtos.user.UserSummaryDTO;

public record MessageResponse(
        String id,
        UserSummaryDTO senderDto,
        String content,
        LocalDateTime timestamp,
        boolean isRead
        ) {

}
