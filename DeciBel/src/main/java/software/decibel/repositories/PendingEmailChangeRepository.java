package software.decibel.repositories;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.PendingEmailChange;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;

public interface PendingEmailChangeRepository extends JpaRepository<PendingEmailChange, Long> {

    Optional<PendingEmailChange> findByToken(Token token);

    Optional<PendingEmailChange> findByUser(User user);

    boolean existsByNewEmailIgnoreCase(String newEmail);

    void deleteByToken_ExpiresAtBefore(LocalDateTime time);

    // Bulk delete by User and Token Type
    void deleteByToken_UserAndToken_TokenType(User user, TokenType tokenType);
}
