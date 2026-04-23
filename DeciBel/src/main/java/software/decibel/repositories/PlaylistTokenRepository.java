package software.decibel.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.PlaylistToken;

public interface PlaylistTokenRepository extends JpaRepository<PlaylistToken, Long> {

    Optional<PlaylistToken> findByPlaylistIdAndIsDeletedFalse(Long playlistId);

    Optional<PlaylistToken> findByTokenAndIsDeletedFalse(String token);

    void deleteByPlaylistId(Long playlistId);

    Optional<PlaylistToken> findByPlaylistId(Long playlistId);
}
