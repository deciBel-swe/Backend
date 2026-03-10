package Software.DeciBel.Entities;

import Software.DeciBel.Enums.AccountTier;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true) //null for Google accounts
    private String passwordHash;

    @Column(unique = true)
    private String googleId; // For social identity login

    @Column(nullable = false)
    private boolean isEmailVerified = false;

    @Column(nullable = false, unique = true)
    private String username;

    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String location;

    @ElementCollection
    private List<String> favoriteGenres;

    private String avatarUrl;
    private String coverPhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountTier tier = AccountTier.LISTENER;

    //    Privacy Toggles 
    @Column(nullable = false)
    private boolean isPrivate = false;

    @Column(nullable = false)
    private boolean showHistory = true;

    private int followerCount = 0;
    private int followingCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
