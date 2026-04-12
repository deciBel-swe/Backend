package software.decibel.services.notification;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import software.decibel.entities.FcmToken;
import software.decibel.repositories.FcmTokenRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmNotificationService {

    private final FcmTokenRepository fcmTokenRepository;

    public void sendNotification(Long userId, String title, String body) {
        List<FcmToken> tokens = fcmTokenRepository.findAllByUserId(userId);

        if (tokens.isEmpty()) {
            log.info("No FCM tokens registered for userId={}", userId);
            return;
        }

        for (FcmToken fcmToken : tokens) {
            try {
                Message message = Message.builder()
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setToken(fcmToken.getToken())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("FCM notification sent: userId={} messageId={}", userId, response);

            } catch (Exception e) {
                log.error("Failed to send FCM notification to token={} userId={}: {}",
                        fcmToken.getToken(), userId, e.getMessage());
            }
        }
    }

    @Transactional
    public void deleteByUserIdAndToken(long userId, String token) {
        fcmTokenRepository.deleteByUserIdAndToken(userId, token);
    }

}
