package software.decibel.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import software.decibel.entities.Follow;
import software.decibel.entities.User;

import java.util.Optional;

// Repository for Follow entity operations
@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    // Checks if a follow relationship exists
    boolean existsByFollowerAndFollowing(User follower, User following);

    // Finds a specific follow relationship
    Optional<Follow> findByFollowerAndFollowing(User follower, User following);

    // Retrieves followers of a user
    Page<Follow> findByFollowing(User following, Pageable pageable);

    // Retrieves users followed by a user
    Page<Follow> findByFollower(User follower, Pageable pageable);

    // Counts followers of a user
    long countByFollowing(User following);

    // Counts users followed by a user
    long countByFollower(User follower);
}
