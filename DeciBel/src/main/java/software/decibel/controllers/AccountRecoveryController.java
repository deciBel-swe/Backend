package software.decibel.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.ForgotPasswordRequest;
import software.decibel.dtos.ForgotPasswordResponse;
import software.decibel.dtos.ResetPasswordRequest;
import software.decibel.dtos.ResetPasswordResponse;
import software.decibel.services.AccountRecoveryService;

@RestController
@RequestMapping("/auth")
public class AccountRecoveryController {

    private final AccountRecoveryService accountRecoveryService;

    public AccountRecoveryController(AccountRecoveryService accountRecoveryService) {
        this.accountRecoveryService = accountRecoveryService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        accountRecoveryService.forgotPassword(request.email());

        return ResponseEntity.ok(
                new ForgotPasswordResponse(
                        "If an account with that email exists, a reset link has been sent."
                )
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        accountRecoveryService.resetPassword(request.token(), request.newPassword());

        return ResponseEntity.ok(
                new ResetPasswordResponse("Password reset successful.")
        );
    }
}