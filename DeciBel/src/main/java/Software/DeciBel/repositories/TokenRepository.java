package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {

    /**
     * Finds an unconsumed token by hash and functional type.
     */
    Optional<Token> findByHashAndTokenTypeAndUsedAtIsNull(String hash, TokenType tokenType);

    /**
     * Returns active (unused and unexpired) tokens for a user and token purpose.
     */
    List<Token> findAllByUserAndTokenTypeAndUsedAtIsNullAndExpiresAtAfter(
            User user,
            TokenType tokenType,
            LocalDateTime now
    );
}
