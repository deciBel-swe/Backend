package software.decibel.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import software.decibel.enums.TrackAccess;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;

@Entity
@Table(name = "tracks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Track Metadata ---
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate releaseDate;

    @Column(nullable = false)
    private String genre;

    private String description;
    private int durationSeconds;

    @Builder.Default
    private int likeCount = 0;
    @Builder.Default
    private int repostCount = 0;
    @Builder.Default
    private int playCount = 0;
    @Builder.Default
    private int completedPlayCount = 0;
    @Builder.Default
    private int commentCount = 0;
    @Builder.Default
    private double playThroughRate = 0.0;

    @Enumerated(EnumType.STRING)
    private TrackState state;

    @Enumerated(EnumType.STRING)
    private TrackAccess access;

    @CreationTimestamp
    private LocalDateTime uploadDate;

    // ---Visibility ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    // --- File & Storage Details ---
    private String trackUrl;
    private String trackPreviewUrl;
    private String coverUrl;
    private String waveformUrl;

    // ---- publishing ----
    @Column(unique = true)
    private String slug;

    @Builder.Default
    private boolean published = false;
    private LocalDateTime publishedAt;

    // --- Relationships ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    // Many tracks can have many independent tags
    @ManyToMany
    @JoinTable(
            name = "track_tags",
            joinColumns = @JoinColumn(name = "track_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @Builder.Default
    private List<Tag> tags = new ArrayList<>();

    // A track has many secret tokens (deleted once track is deleted)
    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TrackToken> tokens = new ArrayList<>();

    // track can have many comments
    // purpose is to delete comments if track is deleted
    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();
}
