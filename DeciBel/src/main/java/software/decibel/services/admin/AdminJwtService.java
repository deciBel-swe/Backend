package software.decibel.services.admin;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import software.decibel.entities.Admin;

@Service
public class AdminJwtService {

    public static final long ADMIN_TOKEN_EXPIRES_IN_SECONDS = 12L * 60L * 60L; // 12 hours

    private final String activeProfile;
    private SecretKey jwtSigningKey;

    public AdminJwtService(@Value("${spring.profiles.active:default}") String activeProfile) {
        this.activeProfile = activeProfile;
    }

    @PostConstruct
    void initSigningKey() {
        String envSecret = System.getenv("ADMIN_JWT_SECRET");
        if ((envSecret == null || envSecret.isBlank())
                && !"default".equals(activeProfile)
                && !"local".equals(activeProfile)
                && !"dev".equals(activeProfile)) {
            throw new IllegalStateException(
                    "ADMIN_JWT_SECRET environment variable is missing in non-local environment: " + activeProfile);
        }

        String secretToUse = envSecret;
        // For local development, use a default secret (TODO: Remove on Production)
        if (envSecret == null || envSecret.isBlank()) {
            secretToUse = "YWRtaW4tZGVjaWJlbC1kZXYtb25seS1qd3Qtc2VjcmV0LWNoYW5nZS1iZWZvcmUtcHJvZA=="; 
        }

        byte[] keyBytes = Decoders.BASE64.decode(secretToUse);
        this.jwtSigningKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String buildAdminToken(Admin admin) {
        Date issuedAt = new Date();
        Date expiresAt = Date.from(LocalDateTime.now()
                .plusSeconds(ADMIN_TOKEN_EXPIRES_IN_SECONDS)
                .toInstant(ZoneOffset.UTC));

        return Jwts.builder()
                .subject(String.valueOf(admin.getId()))
                .claim("email", admin.getEmail())
                .claim("username", admin.getUsername())
                .claim("role", "ADMIN")
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(jwtSigningKey)
                .compact();
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(jwtSigningKey).build().parseSignedClaims(token).getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}
