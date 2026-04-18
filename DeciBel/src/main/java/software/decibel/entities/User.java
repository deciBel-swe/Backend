package software.decibel.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import software.decibel.enums.AccountTier;

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
    @Builder.Default
    private AccountTier tier = AccountTier.FREE;

    // Privacy Toggles 
    @Column(nullable = false)
    @Builder.Default
    private boolean isPrivate = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean showHistory = true;

    @Builder.Default
    private int followerCount = 0;
    @Builder.Default
    private int followingCount = 0;
    @Builder.Default
    private int trackCount = 0;

  @Builder.Default private int freeTracksLeft = 3;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // relationships
    // user can have many comments
    // purpose is to delete comments if user is deleted
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments;
}
