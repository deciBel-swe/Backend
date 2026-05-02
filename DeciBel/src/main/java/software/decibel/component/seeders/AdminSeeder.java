package software.decibel.component.seeders;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.entities.Admin;
import software.decibel.repositories.AdminRepository;

/**
 * Automatically inserts a master admin account when the application starts if
 * one does not already exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        String adminEmail = "admin@decibel.software";

        if (adminRepository.findByEmail(adminEmail).isEmpty()) {
            log.info("No master admin found. Generating default admin account...");

            Admin defaultAdmin = Admin.builder()
                    .email(adminEmail)
                    .username("master_admin")
                    .password(passwordEncoder.encode("admin123!")) // Set a temporary password
                    .avatarUrl("https://example.com/admin.png")
                    .deviceInfo("DESKTOP|InitialSeeder")
                    .build();

            adminRepository.save(defaultAdmin);

            log.info("Default Admin Account Configured:");
            log.info("Email: {}", adminEmail);
            log.info("Password: {}", "admin123!");
        }
    }
}
