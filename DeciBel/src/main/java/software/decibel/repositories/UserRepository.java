package software.decibel.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);
}
