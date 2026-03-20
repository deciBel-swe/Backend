package software.decibel.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.user.TierUpgradeRequest;
import software.decibel.dtos.user.TierUpgradeResponse;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.TokenType;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.TierException;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.UserRepository;

import java.util.List;

@Service
public class UserTierService {

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final SessionService sessionService;

    public UserTierService(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            JwtService jwtService,
            TokenService tokenService,
            SessionService sessionService) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.jwtService = jwtService;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
    }

    @Transactional
    public TierUpgradeResponse upgradeTier(Long userId, TierUpgradeRequest request, DeviceInfo deviceInfo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        AccountTier requestedTier = request.targetTier();

        if (requestedTier == user.getTier()) {
            throw new TierException("You are already on the " + requestedTier + " tier");
        }

        if (requestedTier == AccountTier.FREE) {
            throw new TierException("Cannot downgrade to FREE tier");
        }

        // Resolve email from any identity — all identities for a user share the same email
        List<AuthIdentity> identities = authIdentityRepository.findAllByUser(user);
        if (identities.isEmpty()) {
            throw new ResourceNotFoundException("No auth identity found for user " + userId);
        }
        String email = identities.get(0).getEmail();

        // Apply tier change
        user.setTier(requestedTier);
        userRepository.save(user);

        // Rotate refresh token — invalidate all existing sessions and issue a fresh one
        sessionService.deleteAllSessionsForUser(user);
        tokenService.deleteTokensForUserAndType(user, TokenType.REFRESH_TOKEN);

        TokenService.IssuedToken issuedRefreshToken = tokenService.createRefreshToken(user);
        sessionService.createSession(user, issuedRefreshToken.token(), deviceInfo);

        // Issue new access token carrying updated tier claim
        String newAccessToken = jwtService.buildAccessToken(user, email);

        return new TierUpgradeResponse(
                user.getTier(),
                "Tier updated to " + user.getTier(),
                newAccessToken,
                issuedRefreshToken.rawToken(),
                JwtService.REFRESH_TOKEN_EXPIRES_IN_SECONDS
        );
    }
}
