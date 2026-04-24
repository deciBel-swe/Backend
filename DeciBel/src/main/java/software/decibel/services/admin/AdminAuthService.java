package software.decibel.services.admin;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.admin.AdminUserResponse;
import software.decibel.dtos.admin.LoginAdminRequest;
import software.decibel.dtos.admin.LoginAdminResponse;
import software.decibel.entities.Admin;
import software.decibel.exceptions.custom.InvalidAdminCredentialsException;
import software.decibel.repositories.AdminRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtService adminJwtService;

    public LoginAdminResponse login(LoginAdminRequest request) {
        // Find by email according to OpenAPI Spec
        Admin admin = adminRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidAdminCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw new InvalidAdminCredentialsException("Invalid email or password.");
        }

        // Optionally record the latest device info
        String newDeviceInfo = request.deviceInfo().deviceType().name() + "|" + request.deviceInfo().deviceName();
        if (admin.getDeviceInfo() == null || !admin.getDeviceInfo().equals(newDeviceInfo)) {
            admin.setDeviceInfo(newDeviceInfo);
            adminRepository.save(admin);
        }

        String token = adminJwtService.buildAdminToken(admin);

        AdminUserResponse adminUser = AdminUserResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .avatarUrl(admin.getAvatarUrl())
                .build();

        return LoginAdminResponse.builder()
                .accessToken(token)
                .expiresIn(AdminJwtService.ADMIN_TOKEN_EXPIRES_IN_SECONDS)
                .adminUser(adminUser)
                .build();
    }
}
