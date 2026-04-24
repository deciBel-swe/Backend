package software.decibel.dtos.messaging;

import java.time.LocalDateTime;

import software.decibel.dtos.user.UserSummaryDTO;

public record ConversationResponse(
        String id,
        UserSummaryDTO senderDto,
        String lastMessage,
        LocalDateTime lastTimestamp,
        Long unreadCount
        ) {

}
