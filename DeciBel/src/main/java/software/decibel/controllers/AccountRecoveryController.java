package software.decibel.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.auth.ForgotPasswordRequest;
import software.decibel.dtos.auth.ForgotPasswordResponse;
import software.decibel.dtos.auth.ResetPasswordRequest;
import software.decibel.dtos.auth.ResetPasswordResponse;
import software.decibel.services.AccountRecoveryService;

//Controller for handling account recovery operations, such as forgot password and reset password.
@RestController
@RequestMapping("/auth")
public class AccountRecoveryController {

    private final AccountRecoveryService accountRecoveryService;

    public AccountRecoveryController(AccountRecoveryService accountRecoveryService) {
        this.accountRecoveryService = accountRecoveryService;
    }


     // Handles forgot password requests.
     // If the email exists, a password reset link is sent to the user's email.
     // To prevent email enumeration, it returns a generic success message regardless of whether the email exists.
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


     // Handles reset password requests.
     //Validates the provided token and updates the user's password if valid.


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