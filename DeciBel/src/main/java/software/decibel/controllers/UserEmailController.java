package software.decibel.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.user.ChangeEmailRequest;
import software.decibel.dtos.user.VerifyEmailChangeRequest;
import software.decibel.services.UserEmailService;

@RestController
@RequestMapping("/users/me")
public class UserEmailController {

    private final UserEmailService userEmailService;

    public UserEmailController(UserEmailService userEmailService) {
        this.userEmailService = userEmailService;
    }

    @PatchMapping("/email")
    public ResponseEntity<MessageResponse> requestEmailChange(
            Authentication authentication,
            @Valid @RequestBody ChangeEmailRequest request
    ) {
        MessageResponse response = userEmailService.requestMyEmailChange(authentication, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/verify")
    public ResponseEntity<MessageResponse> verifyEmailChange(
            Authentication authentication,
            @Valid @RequestBody VerifyEmailChangeRequest request
    ) {
        MessageResponse response = userEmailService.verifyMyEmailChange(authentication, request);
        return ResponseEntity.ok(response);
    }
}
