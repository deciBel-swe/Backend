package software.decibel.component.seeders;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.auth.RegisterLocalRequest;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.DeviceType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.auth.AuthService;

@Component
@Order(5)
@Profile("dev")
@Slf4j
public class BulkUserSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private static final String RAW_PASSWORD = "SeedUser@1234";
    private static final DeviceInfo SEEDER_DEVICE = new DeviceInfo(DeviceType.WEB, "Seeder", "Mozilla/5.0 (Seeder)");
    private static final LocalDate DEFAULT_DOB = LocalDate.of(1995, 1, 1);

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;

    public BulkUserSeeder(UserRepository userRepository, AuthIdentityRepository authIdentityRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private static final String[] CITIES = {
        "Cairo", "New York", "London", "Tokyo", "Paris",
        "Berlin", "Sydney", "Toronto", "Dubai", "Seoul",
        "Madrid", "Rome", "Amsterdam", "Singapore", "Istanbul",
        "Mexico City", "São Paulo", "Mumbai", "Lagos", "Bangkok"
    };
    private static final String[] COUNTRIES = {
        "Egypt", "USA", "UK", "Japan", "France",
        "Germany", "Australia", "Canada", "UAE", "South Korea",
        "Spain", "Italy", "Netherlands", "Singapore", "Turkey",
        "Mexico", "Brazil", "India", "Nigeria", "Thailand"
    };

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        int created = 0;

        for (int i = 1; i <= 20; i++) {
            String email = "bulk.user" + i + "@decibel.dev";

            if (authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(
                    email, AuthProvider.LOCAL, AuthType.PASSWORD)) {
                continue;
            }

            try {
                String baseUsername = ("bulkuser" + i).toLowerCase();
                User user = User.builder()
                        .username(baseUsername)
                        .displayName("Bulk User " + i)
                        .location(CITIES[i - 1] + ", " + COUNTRIES[i - 1])
                        .build();
                User savedUser = userRepository.save(user);

                AuthIdentity identity = AuthIdentity.builder()
                        .user(savedUser)
                        .email(email)
                        .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                        .emailVerified(true)
                        .provider(AuthProvider.LOCAL)
                        .type(AuthType.PASSWORD)
                        .build();
                authIdentityRepository.save(identity);

                created++;
                log.info("[BulkUserSeeder] Seeded user {}/20: {}", i, email);

            } catch (Exception ex) {
                log.warn("[BulkUserSeeder] Failed to seed user {}: {} — {}", i, email, ex.getMessage());
            }
        }

        if (created == 0) {
            log.info("[BulkUserSeeder] All bulk users already exist — skipping.");
        } else {
            log.info("[BulkUserSeeder] Done. {} users created. Password: {}", created, RAW_PASSWORD);
        }
    }
}
