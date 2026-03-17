package software.decibel.services;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.auth.GoogleOauthRequest;
import software.decibel.dtos.auth.LoginLocalRequest;
import software.decibel.dtos.auth.LoginLocalResponse;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.auth.RegisterLocalRequest;
import software.decibel.dtos.auth.VerifyEmailRequest;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.TokenType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.UserRepository;

@Service
public class AuthService {
    /**
     * Refresh token lifetime in seconds (30 days).
     */
    private static final long REFRESH_TOKEN_EXPIRES_IN_SECONDS = 30L * 24L * 60L * 60L;

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SessionService sessionService;
    private final EmailService emailService;
    private final FrontendLinkService frontendLinkService;
    private final JwtService jwtService;
    private final GoogleTokenVerificationService googleTokenVerificationService;

    public AuthService(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            SessionService sessionService,
            EmailService emailService,
            FrontendLinkService frontendLinkService,
            JwtService jwtService,
            GoogleTokenVerificationService googleTokenVerificationService) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.emailService = emailService;
        this.frontendLinkService = frontendLinkService;
        this.jwtService = jwtService;
        this.googleTokenVerificationService = googleTokenVerificationService;
    }

    @Transactional
    public MessageResponse registerLocal(RegisterLocalRequest request) {
        if (authIdentityRepository.existsByEmailIgnoreCase(request.email())
                || authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(
                        request.email(), AuthProvider.LOCAL, AuthType.PASSWORD)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = User.builder()
                .username(request.username())
                .location(buildLocation(request.city(), request.country()))
                .build();
        User savedUser = userRepository.save(user);

        AuthIdentity authIdentity = AuthIdentity.builder()
                .user(savedUser)
                .email(request.email())
                .passwordHash(hashedPassword)
                .emailVerified(false)
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .build();
        authIdentityRepository.save(authIdentity);

        // CREATE REAL TOKEN & SEND REAL EMAIL
        TokenService.IssuedToken verificationToken = tokenService.createEmailVerificationToken(savedUser);
        String verificationLink = frontendLinkService.buildEmailVerificationLink(verificationToken.rawToken());
        emailService.sendEmailVerificationEmail(request.email(), verificationLink);

        return new MessageResponse("User Generated successfully");
    }

    @Transactional
    public AuthLoginResult loginLocal(LoginLocalRequest request) {
        AuthIdentity identity = authIdentityRepository
                .findByEmailIgnoreCaseAndProviderAndType(request.email(), AuthProvider.LOCAL, AuthType.PASSWORD)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), identity.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (!identity.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email is not verified");
        }

        return issueLoginTokens(identity, request.deviceInfo());
    }

    @Transactional
    public AuthRefreshTokenResult verifyEmail(VerifyEmailRequest request) {
        Token verificationToken = tokenService.findValidUnusedToken(
                request.token(),
                TokenType.EMAIL_VERIFICATION,
                "Invalid verification token");

        User user = verificationToken.getUser();

        authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD)
                .ifPresent(identity -> {
                    identity.setEmailVerified(true);
                    authIdentityRepository.save(identity);
                });

        tokenService.markTokenUsed(verificationToken);

        // The current API contract for /auth/verify-email accepts only the token,
        // but this flow still issues a refresh token after successful verification.
        return issueRefreshToken(user);
    }

    @Transactional
    public AuthLoginResult loginWithGoogle(GoogleOauthRequest request) {
        GoogleTokenVerificationService.VerifiedGoogleToken verifiedToken = googleTokenVerificationService
                .verifyIdToken(request.authTokenDto());

        AuthIdentity identity = authIdentityRepository
                .findByProviderUserIdAndProviderAndType(
                        verifiedToken.subject(), AuthProvider.GOOGLE, AuthType.OAUTH)
                .orElseGet(() -> registerGoogleIdentity(verifiedToken));

        return issueLoginTokens(identity, request.deviceInfo());
    }

    private AuthLoginResult issueLoginTokens(AuthIdentity identity, DeviceInfo deviceInfo) {
        User user = identity.getUser();
        // JwtService (feat) to ensure Artist/Listener roles are in the token
        String accessToken = jwtService.buildAccessToken(user, identity.getEmail());
        AuthRefreshTokenResult refreshTokenResult = issueRefreshToken(user, deviceInfo);

        LoginLocalResponse response = new LoginLocalResponse(
                accessToken,
                JwtService.ACCESS_TOKEN_EXPIRES_IN_SECONDS,
                new LoginLocalResponse.UserInfo(
                        user.getId(),
                        user.getUsername(),
                        user.getTier(), // Role differentiation
                        "/users/" + user.getUsername(), // TODO: Correct URL structure if needed
                        user.getAvatarUrl()));

        return new AuthLoginResult(response, refreshTokenResult.refreshToken(),
                refreshTokenResult.refreshTokenExpiresIn());
    }

    private AuthRefreshTokenResult issueRefreshToken(User user, DeviceInfo deviceInfo) {
        // Use TokenService for token persistence
        TokenService.IssuedToken issuedToken = tokenService.createRefreshToken(user);
        sessionService.createSession(user, issuedToken.token(), deviceInfo);
        return new AuthRefreshTokenResult(issuedToken.rawToken(), REFRESH_TOKEN_EXPIRES_IN_SECONDS);
    }

    private AuthRefreshTokenResult issueRefreshToken(User user) {
        TokenService.IssuedToken issuedToken = tokenService.createRefreshToken(user);
        return new AuthRefreshTokenResult(issuedToken.rawToken(), REFRESH_TOKEN_EXPIRES_IN_SECONDS);
    }

    private AuthIdentity registerGoogleIdentity(
            GoogleTokenVerificationService.VerifiedGoogleToken verifiedToken) {
        if (authIdentityRepository.existsByEmailIgnoreCase(verifiedToken.email())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists and is not linked to Google.");
        }

        User savedUser = userRepository.save(User.builder()
                .username(generateUniqueUsername(verifiedToken))
                .displayName(resolveDisplayName(verifiedToken))
                .avatarUrl(verifiedToken.picture())
                .build());

        AuthIdentity googleIdentity = AuthIdentity.builder()
                .user(savedUser)
                .email(verifiedToken.email())
                .providerUserId(verifiedToken.subject())
                .emailVerified(verifiedToken.emailVerified())
                .provider(AuthProvider.GOOGLE)
                .type(AuthType.OAUTH)
                .build();

        return authIdentityRepository.save(googleIdentity);
    }

    private String generateUniqueUsername(
            GoogleTokenVerificationService.VerifiedGoogleToken verifiedToken) {
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

    private String resolveBaseUsername(
            GoogleTokenVerificationService.VerifiedGoogleToken verifiedToken) {
        if (verifiedToken.name() != null && !verifiedToken.name().isBlank()) {
            return verifiedToken.name();
        }

        int emailSeparatorIndex = verifiedToken.email().indexOf('@');
        if (emailSeparatorIndex > 0) {
            return verifiedToken.email().substring(0, emailSeparatorIndex);
        }

        return verifiedToken.subject();
    }

    private String sanitizeUsername(String rawValue) {
        return rawValue.toLowerCase()
                .replaceAll("[^a-z0-9._]", "")
                .trim();
    }

    private String buildGoogleUsernameCandidate(String baseUsername, String googleSubject) {
        String normalizedBase = baseUsername.length() > 20
                ? baseUsername.substring(0, 20)
                : baseUsername;
        String subjectSuffix = googleSubject.length() > 6
                ? googleSubject.substring(googleSubject.length() - 6)
                : googleSubject;
        return normalizedBase + "_" + subjectSuffix.toLowerCase();
    }

    private String randomUsernameSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private String resolveDisplayName(
            GoogleTokenVerificationService.VerifiedGoogleToken verifiedToken) {
        if (verifiedToken.name() == null || verifiedToken.name().isBlank()) {
            return null;
        }

        return verifiedToken.name().trim();
    }

    private String buildLocation(String city, String country) {
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

    public record AuthLoginResult(
            LoginLocalResponse response,
            String refreshToken,
            long refreshTokenExpiresIn) {
    }

    public record AuthRefreshTokenResult(
            String refreshToken,
            long refreshTokenExpiresIn) {
    }
}
