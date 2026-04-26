package software.decibel.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    //Optional<Token> findByTokenHash(String tokenHash);
    Optional<Token> findByHashAndTokenType(String Hash, TokenType tokenType);

    List<Token> findAllByUserAndTokenType(User user, TokenType tokenType);

    void deleteByUserAndTokenType(User user, TokenType tokenType);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    //find the most recent token of a specific type for a user
    Optional<Token> findFirstByUserAndTokenTypeOrderByCreatedAtDesc(User user, TokenType type);

    /**
     * Finds an unconsumed token by hash and functional type.
     */
    Optional<Token> findByHashAndTokenTypeAndUsedAtIsNull(String hash, TokenType tokenType);

    /**
     * Returns active (unused and unexpired) tokens for a user and token
     * purpose.
     */
    List<Token> findAllByUserAndTokenTypeAndUsedAtIsNullAndExpiresAtAfter(
            User user,
            TokenType tokenType,
            LocalDateTime now
    );
}
