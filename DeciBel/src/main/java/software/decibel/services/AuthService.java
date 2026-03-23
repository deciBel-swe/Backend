package software.decibel.services;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.auth.AuthLoginResult;
import software.decibel.dtos.auth.AuthRefreshTokenResult;
import software.decibel.dtos.auth.AuthTokenRotationResult;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.auth.GoogleOauthRequest;
import software.decibel.dtos.auth.IssuedToken;
import software.decibel.dtos.auth.LoginLocalRequest;
import software.decibel.dtos.auth.LoginLocalResponse;
import software.decibel.dtos.auth.LogoutSessionRequest;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.auth.RefreshTokenResponse;
import software.decibel.dtos.auth.RegisterLocalRequest;
import software.decibel.dtos.auth.VerifyEmailRequest;
import software.decibel.dtos.auth.google.ResendVerificationEmailRequest;
import software.decibel.dtos.auth.google.VerifiedGoogleToken;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.TokenType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.utils.UserProfileUtility;

@Service
@RequiredArgsConstructor
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
    private final UserProfileUtility userProfileUtility;

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
                .location(userProfileUtility.buildLocation(request.city(), request.country()))
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
        IssuedToken verificationToken = tokenService.createEmailVerificationToken(savedUser);
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

        return issueLoginTokens(identity, request.deviceInfo(), false);
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
        VerifiedGoogleToken verifiedToken = googleTokenVerificationService
                .verifyAuthCode(request.authTokenDto());

        boolean[] isNew = {false};
        AuthIdentity identity = authIdentityRepository
                .findByProviderUserIdAndProviderAndType(
                        verifiedToken.subject(), AuthProvider.GOOGLE, AuthType.OAUTH)
                .orElseGet(() -> {
                    isNew[0] = true;
                    return registerGoogleIdentity(verifiedToken);
                });

        return issueLoginTokens(identity, request.deviceInfo(), isNew[0]);
    }

    @Transactional
    public AuthTokenRotationResult refreshToken(String rawRefreshToken) {
        Token oldToken = tokenService.findValidUnusedToken(
                rawRefreshToken,
                TokenType.REFRESH_TOKEN,
                "Invalid refresh token");

        User user = oldToken.getUser();
        AuthIdentity identity = authIdentityRepository
                .findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User identity not found"));

        // 1- Mark old token as used (Rotation)
        tokenService.markTokenUsed(oldToken);

        // 2- Issue NEW Refresh Token
        AuthRefreshTokenResult newRefreshTokenResult = issueRefreshToken(user);

        // 3- Issue NEW Access Token
        String newAccessToken = jwtService.buildAccessToken(user, identity.getEmail());

        RefreshTokenResponse response = new RefreshTokenResponse(newAccessToken,
                JwtService.ACCESS_TOKEN_EXPIRES_IN_SECONDS);

        return new AuthTokenRotationResult(
                response,
                newRefreshTokenResult.refreshToken(),
                newRefreshTokenResult.refreshTokenExpiresIn());
    }

    @Transactional
    public MessageResponse logout(LogoutSessionRequest request) {
        Token refreshToken = tokenService.findValidUnusedToken(
                request.refreshToken(),
                TokenType.REFRESH_TOKEN,
                "Invalid refresh token");

        sessionService.deleteSessionByRefreshToken(refreshToken);
        tokenService.deleteToken(refreshToken);

        return new MessageResponse("Logged out successfully");
    }

    @Transactional
    public MessageResponse logoutAll(LogoutSessionRequest request) {
        Token refreshToken = tokenService.findValidUnusedToken(
                request.refreshToken(),
                TokenType.REFRESH_TOKEN,
                "Invalid refresh token");

        User user = refreshToken.getUser();
        sessionService.deleteAllSessionsForUser(user);
        tokenService.deleteTokensForUserAndType(user, TokenType.REFRESH_TOKEN);

        return new MessageResponse("Logged out of all sessions");
    }

    @Transactional
    public MessageResponse resendVerificationEmail(ResendVerificationEmailRequest request) {
        AuthIdentity identity = authIdentityRepository
                .findByEmailIgnoreCaseAndProviderAndType(
                        request.email(), AuthProvider.LOCAL, AuthType.PASSWORD)
                .orElse(null);

        // Return silently if email not found — prevent email enumeration
        if (identity == null) {
            return new MessageResponse("If an unverified account exists with that email, a verification link has been sent.");
        }

        // Only resend if not yet verified
        if (identity.isEmailVerified()) {
            return new MessageResponse("If an unverified account exists with that email, a verification link has been sent.");
        }

        User user = identity.getUser();

        // Delete any existing unused verification tokens before issuing a new one
        tokenService.deleteTokensForUserAndType(user, TokenType.EMAIL_VERIFICATION);
        tokenService.deleteExpiredTokens();

        IssuedToken issuedToken = tokenService.createEmailVerificationToken(user);
        String verificationLink = frontendLinkService.buildEmailVerificationLink(issuedToken.rawToken());
        emailService.sendEmailVerificationEmail(identity.getEmail(), verificationLink);

        return new MessageResponse("If an unverified account exists with that email, a verification link has been sent.");
    }

    private AuthLoginResult issueLoginTokens(AuthIdentity identity, DeviceInfo deviceInfo, boolean isNewUser) {
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
                        user.getAvatarUrl(),
                        isNewUser));

        return new AuthLoginResult(response, refreshTokenResult.refreshToken(),
                refreshTokenResult.refreshTokenExpiresIn());
    }

    private AuthRefreshTokenResult issueRefreshToken(User user, DeviceInfo deviceInfo) {
        // Use TokenService for token persistence
        IssuedToken issuedToken = tokenService.createRefreshToken(user);
        sessionService.createSession(user, issuedToken.token(), deviceInfo);
        return new AuthRefreshTokenResult(issuedToken.rawToken(), REFRESH_TOKEN_EXPIRES_IN_SECONDS);
    }

    private AuthRefreshTokenResult issueRefreshToken(User user) {
        IssuedToken issuedToken = tokenService.createRefreshToken(user);
        return new AuthRefreshTokenResult(issuedToken.rawToken(), REFRESH_TOKEN_EXPIRES_IN_SECONDS);
    }

    private AuthIdentity registerGoogleIdentity(VerifiedGoogleToken verifiedToken) {
        if (authIdentityRepository.existsByEmailIgnoreCase(verifiedToken.email())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists and is not linked to Google.");
        }

        User savedUser = userRepository.save(User.builder()
                .username(userProfileUtility.generateUniqueUsername(verifiedToken))
                .displayName(userProfileUtility.resolveDisplayName(verifiedToken))
                .avatarUrl(verifiedToken.pictureUrl())
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
}
