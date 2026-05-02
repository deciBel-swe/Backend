package software.decibel.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import software.decibel.entities.Admin;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.repositories.AdminRepository;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class SeederService {

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void createUser(String email, String username, String displayName,
            String location, String avatarUrl, String rawPassword) {

        if (authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(
                email, AuthProvider.LOCAL, AuthType.PASSWORD)) {
            return;
        }

        User user = User.builder()
                .username(username)
                .displayName(displayName)
                .location(location)
                .avatarUrl(avatarUrl)
                .freeTracksLeft(3)
                .build();

        User savedUser = userRepository.save(user);

        AuthIdentity identity = AuthIdentity.builder()
                .user(savedUser)
                .email(email)
                .passwordHash(passwordEncoder.encode(sha256Hex(rawPassword)))
                .emailVerified(true)
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .build();

        authIdentityRepository.save(identity);
    }

    @Transactional
    public void createAdmin(String email, String username, String rawPassword) {
        if (adminRepository.findByEmail(email).isPresent()) {
            return;
        }

        Admin admin = Admin.builder()
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(sha256Hex(rawPassword)))
                .avatarUrl("https://example.com/avatars/" + username + ".png")
                .deviceInfo("DESKTOP|BulkAdminSeeder")
                .build();

        adminRepository.save(admin);
    }

    public boolean userExists(String email) {
        return authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(
                email, AuthProvider.LOCAL, AuthType.PASSWORD);
    }

    public boolean adminExists(String email) {
        return adminRepository.findByEmail(email).isPresent();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
