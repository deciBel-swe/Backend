package software.decibel.dtos.messaging;

import java.time.LocalDateTime;

public record MessageResponse(
    String id,
    Long senderId,
    Long recipientId,
    String content,
    LocalDateTime timestamp
) {}
