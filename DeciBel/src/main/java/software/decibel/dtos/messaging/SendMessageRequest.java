package software.decibel.dtos.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(
    @NotNull(message = "Recipient ID is required")
    Long recipientId,
    
    @NotBlank(message = "Message content cannot be empty")
    String content
) {}
