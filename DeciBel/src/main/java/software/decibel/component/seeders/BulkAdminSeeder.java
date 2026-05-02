package software.decibel.component.seeders;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.entities.Admin;
import software.decibel.repositories.AdminRepository;

/**
 * Seeds multiple admin accounts for development and testing. Each admin has a
 * unique email and username but shares the same password for convenience.
 */
@Component
@Order(6)
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class BulkAdminSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String RAW_PASSWORD = "Admin@1234";

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {

        // SHA-256 first (frontend), then BCrypt (server storage)
        String storedPassword = passwordEncoder.encode(RAW_PASSWORD);

        int created = 0;

        for (int i = 1; i <= 6; i++) {
            String email = "bulk.admin" + i + "@decibel.dev";
            String username = "bulk_admin_" + i;

            if (adminRepository.findByEmail(email).isPresent()) {
                continue;
            }

            Admin admin = Admin.builder()
                    .email(email)
                    .username(username)
                    .password(storedPassword)
                    .avatarUrl("https://example.com/avatars/admin" + i + ".png")
                    .deviceInfo("DESKTOP|BulkAdminSeeder")
                    .build();

            adminRepository.save(admin);
            created++;

        }

        if (created == 0) {
            log.info("[BulkAdminSeeder] All bulk admins already exist — skipping.");
        } else {
            log.info("[BulkAdminSeeder] Done. {} admins created. Raw password: {}", created, RAW_PASSWORD);
        }
    }

}
