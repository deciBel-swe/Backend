package software.decibel.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.notifications.*;
import software.decibel.services.notification.InAppNotificationService;
import software.decibel.services.notification.FcmNotificationService;
import software.decibel.services.JwtService;
import software.decibel.entities.FcmToken;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.FcmTokenRepository;
import software.decibel.repositories.UserRepository;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final InAppNotificationService notificationService;
    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;
    private final FcmNotificationService fcmNotificationService;

    // GET /notifications
    @GetMapping
    public ResponseEntity<NotificationPageResponse> getNotifications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        Long userId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(notificationService.getNotifications(userId, page, size));
    }

    // POST /notifications/mark-all-read
    @PostMapping("/mark-all-read")
    public ResponseEntity<MessageResponse> markAllRead() {
        Long userId = JwtService.getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(new MessageResponse("All notifications marked as read"));
    }

    // GET /notifications/unread-count
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        Long userId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    // GET /notifications/settings
    @GetMapping("/settings")
    public ResponseEntity<NotificationSettingsDto> getSettings() {
        Long userId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(notificationService.getPreferences(userId));
    }

    // PATCH /notifications/settings
    @PatchMapping("/settings")
    public ResponseEntity<NotificationSettingsDto> updateSettings(
            @RequestBody UpdateNotificationSettingsRequest request) {
        Long userId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(notificationService.updatePreferences(userId, request));
    }

    // POST /users/me/device-tokens — register FCM device token
    @PostMapping("/device-tokens")
    public ResponseEntity<MessageResponse> registerDeviceToken(
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        Long userId = JwtService.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check for duplicate
        if (fcmTokenRepository.findByUserIdAndToken(userId, request.token()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new MessageResponse("Device token already registered"));
        }

        fcmTokenRepository.save(FcmToken.builder()
                .user(user)
                .token(request.token())
                .deviceName(request.deviceType().name())
                .build());

        return ResponseEntity.ok(new MessageResponse("Device registered successfully"));
    }

    // DELETE /notifications/device-tokens — unregister FCM token
    @DeleteMapping("/device-tokens")
    public ResponseEntity<Void> unregisterDeviceToken(@RequestParam String token) {
        Long userId = JwtService.getCurrentUserId();
        fcmNotificationService.deleteByUserIdAndToken(userId, token);
        return ResponseEntity.noContent().build();
    }

}
