package software.decibel.controllers.User;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.user.ChangePasswordRequest;
import software.decibel.services.user.UserPasswordService;

@RestController
@RequestMapping("/users/me")
public class UserPasswordController {

    private final UserPasswordService userPasswordService;

    public UserPasswordController(UserPasswordService userPasswordService) {
        this.userPasswordService = userPasswordService;
    }

    @PatchMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        MessageResponse response = userPasswordService.resetMyPassword(authentication, request);
        return ResponseEntity.ok(response);
    }
}
