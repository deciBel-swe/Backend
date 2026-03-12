package software.decibel.controllers;

import software.decibel.dtos.PrivacyUpdateRequest;
import software.decibel.dtos.PrivacyUpdateResponse;
import software.decibel.services.UserPrivacyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
public class UserPrivacyController {

    private final UserPrivacyService userPrivacyService;

    public UserPrivacyController(UserPrivacyService userPrivacyService) {
        this.userPrivacyService = userPrivacyService;
    }

    @PatchMapping("/privacy")
    public ResponseEntity<PrivacyUpdateResponse> updatePrivacy(
            Authentication authentication,
            @Valid @RequestBody PrivacyUpdateRequest request
    ) {
        PrivacyUpdateResponse response = userPrivacyService.updateMyPrivacy(authentication, request);
        return ResponseEntity.ok(response);
    }
}
