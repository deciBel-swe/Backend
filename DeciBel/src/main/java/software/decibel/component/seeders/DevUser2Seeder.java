package software.decibel.component.seeders;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.UserRepository;

/**
 * Seeds a default regular user for development and testing. The seeded user is
 * pre-verified to skip the registration flow.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DevUser2Seeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        String userEmail = "user2@example.com";
        String userName = "demo_user2";

        if (!authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(
                userEmail, AuthProvider.LOCAL, AuthType.PASSWORD)) {
            log.info("No demo user 2 found. Seeding demo user 2 account...");

            User demoUser
                    = User.builder()
                            .username(userName)
                            .displayName("Demo User 2")
                            .avatarUrl("https://example.com/user2.png")
                            .build();

            User savedUser = userRepository.save(demoUser);

            AuthIdentity authIdentity
                    = AuthIdentity.builder()
                            .user(savedUser)
                            .email(userEmail)
                            .passwordHash(passwordEncoder.encode("1234"))
                            .emailVerified(true) // Skip verification
                            .provider(AuthProvider.LOCAL)
                            .type(AuthType.PASSWORD)
                            .build();

            authIdentityRepository.save(authIdentity);

            log.info("Demo User 2 account seeded:");
            log.info("Email: {}", userEmail);
            log.info("Password: {}", "1234");
            log.info("Username: {}", userName);
        }
    }
}
