package software.decibel.services;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.entities.User;

@Service
public class JwtService {

    public static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 15L * 60L;
    public static final long REFRESH_TOKEN_EXPIRES_IN_SECONDS = 14L * 24L * 60L * 60L;

    private final String activeProfile;
    private SecretKey jwtSigningKey;

    public JwtService(
            @Value("${spring.profiles.active:default}") String activeProfile) {
        this.activeProfile = activeProfile;
    }

    public static Long getCurrentUserId() {
        UserPrincipal principal
                = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getId();
    }

    @PostConstruct
    void initSigningKey() {
        // Retrieve JWT secret from environment variables
        String envSecret = System.getenv("JWT_SECRET");
        // Fail startup if secret is missing in non-local/dev profiles
        if ((envSecret == null || envSecret.isBlank())
                && !"default".equals(activeProfile)
                && !"local".equals(activeProfile)
                && !"dev".equals(activeProfile)) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is missing in non-local environment: " + activeProfile);
        }

        String secretToUse = envSecret;
        if (envSecret == null || envSecret.isBlank()) {
            // Temporary hardcoded fallback strictly FOR LOCAL DEVELOPMENT ONLY
            secretToUse = "ZGVjaWJlbC1kZXYtb25seS1qd3Qtc2VjcmV0LWNoYW5nZS1iZWZvcmUtcHJvZA==";
        }

        byte[] keyBytes = Decoders.BASE64.decode(secretToUse);
        this.jwtSigningKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Builds a short-lived JWT access token carrying the minimum identity
     * claims required for frontend session management and authorization.
     */
    public String buildAccessToken(User user, String email) {
        return buildToken(user, email, ACCESS_TOKEN_EXPIRES_IN_SECONDS);
    }

    /**
     * Builds a long-lived JWT refresh token used for session renewal.
     */
    public String buildRefreshToken(User user, String email) {
        return buildToken(user, email, REFRESH_TOKEN_EXPIRES_IN_SECONDS);
    }

    private String buildToken(User user, String email, long expiresInSeconds) {
        Date issuedAt = new Date();
        Date expiresAt
                = Date.from(
                        LocalDateTime.now()
                                .plusSeconds(expiresInSeconds)
                                .toInstant(ZoneOffset.UTC));

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", email)
                .claim("username", user.getUsername())
                .claim("tier", user.getTier().name()) // Include role for easy frontend access
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(jwtSigningKey)
                .compact();
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Higher-order function to extract specific information from JWT claims
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Parse and verify token signature using the secret key
    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(jwtSigningKey).build().parseSignedClaims(token).getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            // Any parsing exception (signature mismatch, expired, malformed) results in invalid token
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
