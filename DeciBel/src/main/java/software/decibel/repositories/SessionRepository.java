package software.decibel.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.Session;
import software.decibel.entities.Token;
import software.decibel.entities.User;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByRefreshToken(Token refreshToken);

    List<Session> findAllByUser(User user);

    void deleteByUser(User user);
}
