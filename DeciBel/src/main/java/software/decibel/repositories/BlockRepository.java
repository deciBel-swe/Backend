package software.decibel.repositories;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import software.decibel.entities.Block;
import software.decibel.entities.User;

/**
 * Repository for database operations related to User blocks.
 */
@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    /**
     * Find a block relationship between a blocker and a blocked user.
     *
     * @param blocker the user who performed the block
     * @param blocked the user who was blocked
     * @return an Optional containing the block if it exists
     */
    Optional<Block> findByBlockerAndBlocked(User blocker, User blocked);

    /**
     * Check if a block relationship exists between a blocker and a blocked user.
     *
     * @param blocker the user who performed the block
     * @param blocked the user who was blocked
     * @return true if the block exists, false otherwise
     */
    boolean existsByBlockerAndBlocked(User blocker, User blocked);

    /**
     * Find all users blocked by a specific user.
     *
     * @param blocker  the user who performed the blocks
     * @param pageable pagination information
     * @return a page of block relationships
     */
    Page<Block> findByBlocker(User blocker, Pageable pageable);

    /**
     * Remove a block relationship.
     *
     * @param blocker the user who performed the block
     * @param blocked the user who was blocked
     */
    void deleteByBlockerAndBlocked(User blocker, User blocked);
}
