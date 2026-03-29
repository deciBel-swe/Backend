package software.decibel.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.PlaylistSlug;

public interface PlaylistSlugRepository extends JpaRepository<PlaylistSlug, Long> {

    Optional<PlaylistSlug> findBySlugAndIsDeletedFalse(String slug);

}
