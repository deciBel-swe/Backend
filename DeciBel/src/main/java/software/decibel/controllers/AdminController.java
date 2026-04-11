package software.decibel.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.admin.LoginAdminRequest;
import software.decibel.dtos.admin.LoginAdminResponse;
import software.decibel.services.AdminAuthService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ResponseEntity<LoginAdminResponse> login(@Valid @RequestBody LoginAdminRequest request) {
        return ResponseEntity.ok(adminAuthService.login(request));
    }
}
