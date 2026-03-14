package software.decibel.services;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
import software.decibel.repositories.TokenRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.utils.PasswordUtility;
import software.decibel.utils.TokenUtility;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Service
public class AuthService {
    /**
     * Refresh token lifetime in seconds (30 days).
     */
    private static final long REFRESH_TOKEN_EXPIRES_IN_SECONDS = 30L * 24L * 60L * 60L;

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final TokenRepository tokenRepository;
    private final PasswordUtility passwordUtility;
    private final TokenUtility tokenUtility;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            TokenRepository tokenRepository,
            PasswordUtility passwordUtility,
            TokenUtility tokenUtility,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.tokenRepository = tokenRepository;
        this.passwordUtility = passwordUtility;
        this.tokenUtility = tokenUtility;
        this.jwtService = jwtService;
    }

    @Transactional
    public MessageResponse registerLocal(RegisterLocalRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()
                || authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(
                        request.email(), AuthProvider.LOCAL, AuthType.PASSWORD)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        String hashedPassword = passwordUtility.hashPassword(request.password());
        User user = User.builder()
                .email(request.email())
                .username(request.username())
                .passwordHash(hashedPassword)
                .isEmailVerified(false)
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

        String rawVerificationToken = tokenUtility.generateToken();
        String verificationTokenHash = tokenUtility.hashToken(rawVerificationToken);
        Token verificationToken = Token.builder()
                .user(savedUser)
                .tokenType(TokenType.EMAIL_VERIFICATION)
                .hash(verificationTokenHash)
                .expiresAt(tokenUtility.expiresInMinutes(30))
                .build();
        tokenRepository.save(verificationToken);
        

        // TODO: Make Email sending logic.
        return new MessageResponse("User Generated successfully");
    }

    @Transactional
    public AuthLoginResult loginLocal(LoginLocalRequest request) {
        AuthIdentity identity = authIdentityRepository
                .findByEmailIgnoreCaseAndProviderAndType(request.email(), AuthProvider.LOCAL, AuthType.PASSWORD)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordUtility.matches(request.password(), identity.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        /*
         * Must have verified email to login. This is a security measure to prevent
         * abuse and ensure users have access to the email they registered with, which
         * is important for account recovery, notifications, and other features.
         */
        if (!identity.isEmailVerified() || !identity.getUser().isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email is not verified");
        }

        return issueLoginTokens(identity.getUser());
    }

    @Transactional
    public AuthRefreshTokenResult verifyEmail(VerifyEmailRequest request) {
        String tokenHash = tokenUtility.hashToken(request.token());
        // Verification token must be valid, unexpired, and one-time-use (usedAt is
        // null).
        Token verificationToken = tokenRepository
                .findByHashAndTokenTypeAndUsedAtIsNull(tokenHash, TokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token"));

        if (tokenUtility.isExpired(verificationToken.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token has expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD)
                .ifPresent(identity -> {
                    identity.setEmailVerified(true);
                    authIdentityRepository.save(identity);
                });

        verificationToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(verificationToken);

        return issueRefreshToken(user);
    }

    /**
     * Issues access/refresh tokens and returns both API response payload and cookie
     * token value.
     */
    private AuthLoginResult issueLoginTokens(User user) {
        String accessToken = jwtService.buildAccessToken(user);
        AuthRefreshTokenResult refreshTokenResult = issueRefreshToken(user);

        LoginLocalResponse response = new LoginLocalResponse(
                accessToken,
                JwtService.ACCESS_TOKEN_EXPIRES_IN_SECONDS,
                new LoginLocalResponse.UserInfo(
                        user.getId(),
                        user.getUsername(),
                        user.getTier(),
                        null,
                        user.getAvatarUrl()));

        return new AuthLoginResult(response, refreshTokenResult.refreshToken(),
                refreshTokenResult.refreshTokenExpiresIn());
    }

    private AuthRefreshTokenResult issueRefreshToken(User user) {
        String refreshToken = tokenUtility.generateToken(48);
        String refreshTokenHash = tokenUtility.hashToken(refreshToken);

        Token refreshTokenEntity = Token.builder()
                .user(user)
                .tokenType(TokenType.REFRESH_TOKEN)
                .hash(refreshTokenHash)
                .expiresAt(tokenUtility.expiresInMinutes(60L * 24L * 30L))
                .build();
        tokenRepository.save(refreshTokenEntity);

        return new AuthRefreshTokenResult(refreshToken, REFRESH_TOKEN_EXPIRES_IN_SECONDS);
    }

    /**
     * Normalizes optional city/country inputs into a single display location.
     */
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

    /**
     * Transport object used by controller to set refresh cookie while returning
     * response body separately.
     */
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
