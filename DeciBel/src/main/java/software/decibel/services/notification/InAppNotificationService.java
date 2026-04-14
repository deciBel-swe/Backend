package software.decibel.services.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.notifications.NotificationDto;
import software.decibel.dtos.notifications.NotificationPageResponse;
import software.decibel.dtos.notifications.NotificationResourceDto;
import software.decibel.dtos.notifications.NotificationSettingsDto;
import software.decibel.dtos.notifications.UnreadCountResponse;
import software.decibel.dtos.notifications.UpdateNotificationSettingsRequest;
import software.decibel.dtos.user.UserSummaryDTO;
import software.decibel.entities.Notification;
import software.decibel.entities.NotificationPreferences;
import software.decibel.entities.User;
import software.decibel.enums.NotificationType;
import software.decibel.enums.ResourceType;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.NotificationPreferencesRepository;
import software.decibel.repositories.NotificationRepository;
import software.decibel.repositories.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FcmNotificationService fcmNotificationService;

    //called by the other services (like, repost, follow, comment)
    @Transactional
    public void createNotification(
            Long recipientId,
            Long actorId,
            NotificationType type,
            ResourceType resourceType,
            Long resourceId) {

        // Don't notify yourself
        if (recipientId.equals(actorId)) {
            return;
        }

        User recipient = findUser(recipientId);
        User actor = findUser(actorId);

        // Check user preferences
        NotificationPreferences prefs = preferencesRepository.findByUserId(recipientId)
                .orElse(defaultPreferences(recipient));

        if (!isAllowed(type, prefs)) {
            log.debug("[NOTIF] Blocked by preferences: type={} recipientId={}", type, recipientId);
            return;
        }

        // Persist to DB
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .isRead(false)
                .build());

        // Push via Firebase FCM
        String title;
        if (type == NotificationType.REPLY && resourceType == ResourceType.USER) {
            title = actor.getUsername() + " sent you a message";
        } else {
            title = buildTitle(type, actor);
        }

        fcmNotificationService.sendNotification(
                recipientId,
                title,
                buildBody(type, actor, resourceType));
    }
    // GET /notifications

    public NotificationPageResponse getNotifications(Long userId, int page, int size) {
        Page<Notification> notifPage = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));

        return new NotificationPageResponse(
                notifPage.getContent().stream()
                        .map(n -> toDto(n, userId))
                        .toList(),
                notifPage.getNumber(),
                notifPage.getSize(),
                notifPage.getTotalElements(),
                notifPage.getTotalPages(),
                notifPage.isLast()
        );
    }

    // POST /notifications/mark-all-read
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    // GET /notifications/unread-count
    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(
                notificationRepository.countByRecipientIdAndIsReadFalse(userId));

    }

    // GET /notifications/settings
    public NotificationSettingsDto getPreferences(Long userId) {
        NotificationPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElse(defaultPreferences(findUser(userId)));
        return toSettingsDto(prefs);
    }

    // PATCH /notifications/settings
    @Transactional
    public NotificationSettingsDto updatePreferences(Long userId,
            UpdateNotificationSettingsRequest request) {

        if (request.notifyOnFollow() == null && request.notifyOnLike() == null
                && request.notifyOnRepost() == null && request.notifyOnComment() == null
                && request.notifyOnDM() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Request body must include at least one valid preference field");
        }

        User user = findUser(userId);
        NotificationPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> NotificationPreferences.builder().user(user).build());

        if (request.notifyOnFollow() != null) {
            prefs.setNotifyOnFollow(request.notifyOnFollow());
        }
        if (request.notifyOnLike() != null) {
            prefs.setNotifyOnLike(request.notifyOnLike());
        }
        if (request.notifyOnRepost() != null) {
            prefs.setNotifyOnRepost(request.notifyOnRepost());
        }
        if (request.notifyOnComment() != null) {
            prefs.setNotifyOnComment(request.notifyOnComment());
        }
        if (request.notifyOnDM() != null) {
            prefs.setNotifyOnDM(request.notifyOnDM());
        }

        return toSettingsDto(preferencesRepository.save(prefs));
    }

    //--------------------------helpers--------------------------------------
    private NotificationDto toDto(Notification n, Long viewerUserId) {
        User actor = n.getActor();
        boolean isFollowing = followRepository.existsByFollowerIdAndFollowingId(
                viewerUserId, actor.getId());

        return new NotificationDto(
                n.getId(),
                n.getType(),
                new UserSummaryDTO(
                        actor.getId(),
                        actor.getUsername(),
                        actor.getDisplayName(),
                        actor.getAvatarUrl()
                //to be added later
                //isFollowing,
                // actor.getFollowerCount(),
                //actor.getTrackCount()
                ),
                new NotificationResourceDto(n.getResourceType(), n.getResourceId()),
                n.isRead(),
                n.getCreatedAt()
        );
    }

    private NotificationSettingsDto toSettingsDto(NotificationPreferences prefs) {
        return new NotificationSettingsDto(
                prefs.isNotifyOnFollow(),
                prefs.isNotifyOnLike(),
                prefs.isNotifyOnRepost(),
                prefs.isNotifyOnComment(),
                prefs.isNotifyOnDM()
        );
    }

    private boolean isAllowed(NotificationType type, NotificationPreferences prefs) {
        return switch (type) {
            case FOLLOW ->
                prefs.isNotifyOnFollow();
            case LIKE ->
                prefs.isNotifyOnLike();
            case REPOST ->
                prefs.isNotifyOnRepost();
            case COMMENT, REPLY ->
                prefs.isNotifyOnComment();
            case MESSAGE ->
                prefs.isNotifyOnDM();
        };
    }

    private String buildTitle(NotificationType type, User actor) {
        return switch (type) {
            case FOLLOW ->
                actor.getUsername() + " followed you";
            case LIKE ->
                actor.getUsername() + " liked your content";
            case REPOST ->
                actor.getUsername() + " reposted your content";
            case COMMENT ->
                actor.getUsername() + " commented on your track";
            case REPLY ->
                actor.getUsername() + " replied to your comment";
            case MESSAGE ->
                actor.getUsername() + " sent you a message";
        };
    }

    private String buildBody(NotificationType type, User actor, ResourceType resourceType) {
        if (type == NotificationType.REPLY && resourceType == ResourceType.USER) {
            return "You have a new message from " + actor.getUsername();
        }
        return switch (type) {
            case FOLLOW ->
                "You have a new follower";
            case LIKE ->
                "Your " + resourceType.name().toLowerCase() + " received a like";
            case REPOST ->
                "Your " + resourceType.name().toLowerCase() + " was reposted";
            case COMMENT ->
                "New comment on your track";
            case REPLY ->
                "New reply on your comment";
            case MESSAGE ->
                "You have a new message";
        };
    }

    private NotificationPreferences defaultPreferences(User user) {
        return NotificationPreferences.builder().user(user).build();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                "User with id " + userId + " not found"));
    }
}
