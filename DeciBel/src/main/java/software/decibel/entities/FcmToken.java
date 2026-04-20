package software.decibel.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fcm_tokens", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "token"})})

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // FCM device token from client app
    @Column(nullable = false)
    private String token;

    //helps identify which device this token belongs to
    private String deviceName;

    @CreationTimestamp
    private LocalDateTime registeredAt;
}
