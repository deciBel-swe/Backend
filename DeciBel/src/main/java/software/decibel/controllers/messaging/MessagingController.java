package software.decibel.controllers.messaging;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import software.decibel.dtos.messaging.ConversationResponse;
import software.decibel.dtos.messaging.MessagePageResponse;
import software.decibel.dtos.messaging.MessageResponse;
import software.decibel.dtos.messaging.SendMessageRequest;
import software.decibel.services.messaging.MessagingService;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagingService messagingService;

    @PostMapping("/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            Authentication authentication,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messagingService.sendMessage(authentication, request));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<MessagePageResponse> getMessages(
            Authentication authentication,
            @PathVariable("id") String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(messagingService.getMessages(authentication, conversationId, page, size));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ConversationResponse> startConversation(
            Authentication authentication,
            @PathVariable("id") Long recipientId) {
        return ResponseEntity.ok(messagingService.startConversation(authentication, recipientId));
    }
}
