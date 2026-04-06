package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.PlaylistToken;

import java.util.Optional;

public interface PlaylistTokenRepository extends JpaRepository<PlaylistToken, Long> {

    Optional<PlaylistToken> findByPlaylistIdAndIsDeletedFalse(Long playlistId);

    Optional<PlaylistToken> findByTokenAndIsDeletedFalse(String token);
}
