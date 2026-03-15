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

  // TODO: Handle tags in future iteration

  @ElementCollection private List<String> tags;

  @Column(nullable = false)
  private LocalDate releaseDate;

  private String coverUrl;

  @Column(nullable = false)
  private String genre;

  private String description;

  private int likesCount = 0;
  private int repostsCount = 0;
  private int playsCount = 0;

  private double playThroughRate = 0.0;

  // TODO: Change into enum in the next task
  private String trackState;

  // --- File & Storage Details ---
  @Column(nullable = false)
  private String trackUrl;

  @Column(nullable = false)
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

  @CreationTimestamp private LocalDateTime createdAt;

  @UpdateTimestamp private LocalDateTime updatedAt;
}
