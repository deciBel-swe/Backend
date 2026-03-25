package software.decibel.controllers.User;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.user.TierUpgradeRequest;
import software.decibel.dtos.user.TierUpgradeResponse;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserTierService;

@RestController
@RequestMapping("/users/me")
public class UserTierController {

    private final UserTierService userTierService;
    private final String activeProfile;

    public UserTierController(
            UserTierService userTierService,
            @Value("${spring.profiles.active:default}") String activeProfile) {
        this.userTierService = userTierService;
        this.activeProfile = activeProfile;
    }

    // PATCH /users/me/tier — upgrade the current user's account tier,
    // rotates refresh token and returns a new access token with updated tier claim
    @PatchMapping("/tier")
    public ResponseEntity<TierUpgradeResponse> updateTier(
            @Valid
            @RequestBody TierUpgradeRequest request
    ) {
        Long currentUserId = JwtService.getCurrentUserId();

        TierUpgradeResponse response = userTierService.upgradeTier(
                currentUserId,
                request,
                request.deviceInfo()
        );

        ResponseCookie refreshCookie = buildRefreshCookie(
                response.rawRefreshToken(),
                response.refreshTokenExpiresIn()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

//function to build refresh token cookie with appropriate security settings based on active profile
    private ResponseCookie buildRefreshCookie(String refreshToken, long maxAgeSeconds) {
        boolean isProduction = !"default".equals(activeProfile)
                && !"local".equals(activeProfile)
                && !"dev".equals(activeProfile);
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(isProduction)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
