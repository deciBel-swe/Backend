package software.decibel.dtos.messaging;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationResponse(
    String id,
    List<Long> participants,
    String lastMessage,
    LocalDateTime lastTimestamp
) {}
