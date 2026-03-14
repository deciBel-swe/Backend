package software.decibel.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import software.decibel.entities.User;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    public static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 30L * 60L;

    private final String activeProfile;
    private SecretKey jwtSigningKey;

    public JwtService(@org.springframework.beans.factory.annotation.Value("${spring.profiles.active:default}") String activeProfile) {
        this.activeProfile = activeProfile;
    }

    @PostConstruct
    void initSigningKey() {
        // Retrieve JWT secret from environment variables
        String envSecret = System.getenv("JWT_SECRET");
        // Fail startup if secret is missing in non-local/dev profiles
        if ((envSecret == null || envSecret.isBlank()) && !"default".equals(activeProfile) && !"local".equals(activeProfile) && !"dev".equals(activeProfile)) {
            throw new IllegalStateException("JWT_SECRET environment variable is missing in non-local environment: " + activeProfile);
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
     * Builds a short-lived JWT access token carrying the minimum identity claims
     * required for frontend session management and authorization.
     */
    public String buildAccessToken(User user, String email) {
        Date issuedAt = new Date();
        Date expiresAt = Date
                .from(LocalDateTime.now().plusSeconds(ACCESS_TOKEN_EXPIRES_IN_SECONDS).toInstant(ZoneOffset.UTC));


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
        return Jwts.parser()
                .verifyWith(jwtSigningKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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
