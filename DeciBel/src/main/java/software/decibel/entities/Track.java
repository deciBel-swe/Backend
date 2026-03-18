package software.decibel.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
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

  private String coverUrl;

  @Column(nullable = false)
  private String genre;

  private String description;

  private int likeCount = 0;
  private int repostCount = 0;
  private int playCount = 0;

  private double playThroughRate = 0.0;

  @Enumerated(EnumType.STRING)
  private TrackState state;

  // --- File & Storage Details ---
  private String trackUrl;

  private int durationSeconds;

  // Change this part as you see fit
  private List<Float> waveformData;

  // ---Visibility ---
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private Visibility visibility = Visibility.PUBLIC;

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
  private List<Tag> tags;

  // A track has many secret tokens (deleted once track is deleted)
  @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TrackToken> tokens;

  @CreationTimestamp private LocalDateTime createdAt;

  @UpdateTimestamp private LocalDateTime updatedAt;
}
