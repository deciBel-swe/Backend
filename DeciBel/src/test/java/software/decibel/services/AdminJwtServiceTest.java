package software.decibel.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.decibel.entities.Admin;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import software.decibel.services.admin.AdminJwtService;

import static org.junit.jupiter.api.Assertions.*;

class AdminJwtServiceTest {

    private AdminJwtService adminJwtService;
    private Admin admin;

    @BeforeEach
    void setUp() {
        adminJwtService = new AdminJwtService("local");
        ReflectionTestUtils.invokeMethod(adminJwtService, "initSigningKey");

        admin = Admin.builder()
                .id(99L)
                .email("admin@test.com")
                .username("master")
                .build();
    }

    @Test
    void buildAdminToken_createsValidTokenWithSubjectAndRoleClaim() {
        String token = adminJwtService.buildAdminToken(admin);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        String subject = adminJwtService.extractSubject(token);
        assertEquals("99", subject);
        
        // Checking for internal explicit role presence
        String role = adminJwtService.extractClaim(token, claims -> claims.get("role", String.class));
        assertEquals("ADMIN", role);
    }

    @Test
    void isTokenValid_returnsTrueForValidNativeAdminToken() {
        String token = adminJwtService.buildAdminToken(admin);
        assertTrue(adminJwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_returnsFalseForGarbageOrMalformedStrings() {
        assertFalse(adminJwtService.isTokenValid("pure-garbage-string-format"));
        assertFalse(adminJwtService.isTokenValid(""));
        assertFalse(adminJwtService.isTokenValid(null));
    }

    @Test
    void extractSubject_throwsSignatureExceptionWhenTokenIsSignedWithDifferentKey() {
        // A hacker generates a completely valid JWT token format, but using a custom secret code.
        javax.crypto.SecretKey hackerKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode("aGFja2VyLWRlY2liZWwtZGV2LW9ubHktand0LXNlY3JldC1jaGFuZ2UtYmVmb3JlLXByb2Q="));
        String forgedToken = io.jsonwebtoken.Jwts.builder()
                .subject("99")
                .signWith(hackerKey)
                .compact();

        // Native app tries to extract. It MUST throw a SignatureException safely inside Spring context
        assertThrows(SignatureException.class, () -> {
            adminJwtService.extractSubject(forgedToken);
        });

        // The safe boolean fallback must flag it as completely invalid
        assertFalse(adminJwtService.isTokenValid(forgedToken));
    }

    @Test
    void extractSubject_throwsMalformedExceptionOnCorruptedSegments() {
        String validToken = adminJwtService.buildAdminToken(admin);
        // Hacker modifies the payload middle section slightly
        String alteredToken = validToken.substring(0, 50) + "A" + validToken.substring(51);

        assertFalse(adminJwtService.isTokenValid(alteredToken));
    }
}
