package software.decibel.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.AuthIdentity;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    /**
     * Resolves a local or social identity by normalized email and identity
     * discriminator.
     */
    Optional<AuthIdentity> findByEmailIgnoreCaseAndProviderAndType(String email, AuthProvider provider, AuthType type);

    Optional<AuthIdentity> findByProviderUserIdAndProviderAndType(String providerUserId, AuthProvider provider, AuthType type);

    /**
     * Checks whether an identity already exists for the supplied
     * email/provider/type tuple.
     */
    boolean existsByEmailIgnoreCaseAndProviderAndType(String email, AuthProvider provider, AuthType type);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Locates a specific identity record for a user and authentication channel.
     */
    Optional<AuthIdentity> findByUserAndProviderAndType(User user, AuthProvider provider, AuthType type);

    Optional<AuthIdentity> findFirstByUserId(Long userId);

    List<AuthIdentity> findAllByUser(User user);
}
