package software.decibel.utils;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.auth.google.VerifiedGoogleToken;
import software.decibel.repositories.UserRepository;
import software.decibel.entities.User;
import org.springframework.security.core.Authentication;

@Component
@RequiredArgsConstructor
public class UserProfileUtility {

    private final UserRepository userRepository;

    public String generateUniqueUsername(VerifiedGoogleToken verifiedToken) {
        String baseUsername = sanitizeUsername(resolveBaseUsername(verifiedToken));
        if (baseUsername.isBlank()) {
            baseUsername = "user";
        }

        String candidate = buildGoogleUsernameCandidate(baseUsername, verifiedToken.subject());
        if (userRepository.findByUsername(candidate).isEmpty()) {
            return candidate;
        }
        // TODO: could be optimized more...
        for (int attempt = 0; attempt < 5; attempt++) {
            String fallbackCandidate = buildGoogleUsernameCandidate(
                    baseUsername,
                    verifiedToken.subject() + randomUsernameSuffix());
            if (userRepository.findByUsername(fallbackCandidate).isEmpty()) {
                return fallbackCandidate;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Unable to generate a unique username for the Google account.");
    }

    public String resolveBaseUsername(VerifiedGoogleToken verifiedToken) {
        if (verifiedToken.displayName() != null && !verifiedToken.displayName().isBlank()) {
            return verifiedToken.displayName();
        }

        int emailSeparatorIndex = verifiedToken.email().indexOf('@');
        if (emailSeparatorIndex > 0) {
            return verifiedToken.email().substring(0, emailSeparatorIndex);
        }

        return verifiedToken.subject();
    }

    public String sanitizeUsername(String rawValue) {
        return rawValue.toLowerCase()
                .replaceAll("[^a-z0-9._]", "")
                .trim();
    }

    public String buildGoogleUsernameCandidate(String baseUsername, String googleSubject) {
        String normalizedBase = baseUsername.length() > 20
                ? baseUsername.substring(0, 20)
                : baseUsername;
        String subjectSuffix = googleSubject.length() > 6
                ? googleSubject.substring(googleSubject.length() - 6)
                : googleSubject;
        return normalizedBase + "_" + subjectSuffix.toLowerCase();
    }

    public String randomUsernameSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    public String resolveDisplayName(VerifiedGoogleToken verifiedToken) {
        if (verifiedToken.displayName() == null || verifiedToken.displayName().isBlank()) {
            return null;
        }

        return verifiedToken.displayName().trim();
    }

    public String buildLocation(String city, String country) {
        if ((city == null || city.isBlank()) && (country == null || country.isBlank())) {
            return null;
        }
        if (city == null || city.isBlank()) {
            return country.trim();
        }
        if (country == null || country.isBlank()) {
            return city.trim();
        }
        return city.trim() + ", " + country.trim();
    }

    public User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String principal = authentication.getName();
        if (principal == null || principal.isBlank() || "anonymousUser".equalsIgnoreCase(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        final long userId;
        try {
            userId = Long.parseLong(principal);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid ID format");
        }

        // Always load from DB to ensure we are working with a managed entity for updates
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
