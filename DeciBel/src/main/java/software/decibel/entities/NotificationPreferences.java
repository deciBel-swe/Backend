package software.decibel.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    private boolean notifyOnFollow = true;

    @Builder.Default
    private boolean notifyOnLike = true;

    @Builder.Default
    private boolean notifyOnRepost = true;

    @Builder.Default
    private boolean notifyOnComment = true;

    @Builder.Default
    private boolean notifyOnDM = true;

}
