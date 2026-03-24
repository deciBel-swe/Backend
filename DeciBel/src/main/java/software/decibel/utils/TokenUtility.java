package software.decibel.utils;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class TokenUtility {

    private static final int DEFAULT_TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateToken() {
        return generateToken(DEFAULT_TOKEN_BYTES);
    }

    public String generateToken(int bytesLength) {
        byte[] randomBytes = new byte[bytesLength];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }

    public LocalDateTime expiresInMinutes(long minutes) {
        return LocalDateTime.now().plusMinutes(minutes);
    }

    public boolean isExpired(LocalDateTime expiresAt) {
        // If expiresAt is null, we will consider it as invalid/expired
        return expiresAt == null || expiresAt.isBefore(LocalDateTime.now());
    }
}
