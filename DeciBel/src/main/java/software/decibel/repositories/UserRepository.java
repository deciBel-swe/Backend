package software.decibel.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import software.decibel.entities.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByIdAndIsBannedFalse(Long id);
    Optional<User> findByUsernameAndIsBannedFalse(String username);
    Page<User> findByIsBannedTrue(Pageable pageable);
    long countByIsBannedTrue();

    // function to find users to follow based on common genres or favorite genres
    @Query("""
        SELECT DISTINCT u FROM User u
        WHERE u.id <> :userId
        AND (u.id IN (
            SELECT t.uploader.id FROM Track t
            JOIN TrackLike l ON l.track.id = t.id
            WHERE l.user.id = :userId
            AND t.genre IN :genres
        ) OR EXISTS (
            SELECT 1 FROM u.favoriteGenres fg WHERE fg IN :genres
        ))
        AND NOT EXISTS (
            SELECT 1 FROM Follow f WHERE f.follower.id = :userId AND f.following.id = u.id
        )
        AND NOT EXISTS (
            SELECT 1 FROM Block b WHERE (b.blocker.id = :userId AND b.blocked.id = u.id)
            OR (b.blocker.id = u.id AND b.blocked.id = :userId)
        )
    """)
    List<User> findSuggestedUsersByGenres(Long userId, List<String> genres, Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE u.id <> :userId
        AND NOT EXISTS (
            SELECT 1 FROM Follow f WHERE f.follower.id = :userId AND f.following.id = u.id
        )
        AND NOT EXISTS (
            SELECT 1 FROM Block b WHERE (b.blocker.id = :userId AND b.blocked.id = u.id)
            OR (b.blocker.id = u.id AND b.blocked.id = :userId)
        )
        ORDER BY u.followerCount DESC, u.id ASC
    """)
    List<User> findPopularUsers(Long userId, Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')))
        AND u.isPrivate = false
        AND (:currentUserId IS NULL OR NOT EXISTS (
            SELECT 1 FROM Block b
            WHERE (b.blocker.id = :currentUserId AND b.blocked.id = u.id)
            OR (b.blocker.id = u.id AND b.blocked.id = :currentUserId)
        ))
    """)
    Page<User> searchPublicUsersWithBlocking(String query, Long currentUserId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))) AND u.isPrivate = false")
    Page<User> searchPublicUsers(String query, Pageable pageable);
}
