package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    Optional<Token> findByTokenHash(String tokenHash);

    Optional<Token> findByTokenHashAndTokenType(String tokenHash, TokenType tokenType);

    void deleteByUserAndTokenType(User user, TokenType tokenType);
  void deleteByExpiresAtBefore(LocalDateTime dateTime);
}