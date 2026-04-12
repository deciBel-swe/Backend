package software.decibel.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import software.decibel.dtos.admin.LoginAdminRequest;
import software.decibel.dtos.admin.LoginAdminResponse;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.entities.Admin;
import software.decibel.enums.DeviceType;
import software.decibel.exceptions.custom.InvalidAdminCredentialsException;
import software.decibel.repositories.AdminRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AdminJwtService adminJwtService;

    @InjectMocks
    private AdminAuthService adminAuthService;

    private Admin admin;
    private LoginAdminRequest request;

    @BeforeEach
    void setUp() {
        admin = Admin.builder()
                .id(1L)
                .email("admin@test.com")
                .password("encoded_pass")
                .username("admin")
                .deviceInfo(null) // Initially null to test population logic
                .build();

        request = new LoginAdminRequest("admin@test.com", "raw_pass", new DeviceInfo(DeviceType.DESKTOP, "fp", "Windows"));
    }

    @Test
    void login_whenValidAndDeviceNull_returnsResponseAndSavesDevice() {
        when(adminRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("raw_pass", "encoded_pass")).thenReturn(true);
        when(adminJwtService.buildAdminToken(admin)).thenReturn("token123");

        LoginAdminResponse response = adminAuthService.login(request);

        assertNotNull(response);
        assertEquals("token123", response.accessToken());
        
        // Verify device info was saved (since it was initially null)
        verify(adminRepository, times(1)).save(admin);
        assertEquals("DESKTOP|Windows", admin.getDeviceInfo());
    }

    @Test
    void login_whenValidAndDeviceMatchesExactly_skipsDatabaseSave() {
        // Setup initial admin device exactly matching incoming
        admin.setDeviceInfo("DESKTOP|Windows");

        when(adminRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("raw_pass", "encoded_pass")).thenReturn(true);
        when(adminJwtService.buildAdminToken(admin)).thenReturn("token123");

        LoginAdminResponse response = adminAuthService.login(request);

        assertNotNull(response);
        assertEquals("token123", response.accessToken());
        
        // Ensure no redundant saves occur destroying DB IO thresholds
        verify(adminRepository, never()).save(admin);
    }

    @Test
    void login_whenValidAndDeviceDiffers_updatesDatabaseRow() {
        // Setup initial admin device DIFFERENT from the incoming request
        admin.setDeviceInfo("MOBILE|Android");

        when(adminRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("raw_pass", "encoded_pass")).thenReturn(true);
        when(adminJwtService.buildAdminToken(admin)).thenReturn("token123");

        LoginAdminResponse response = adminAuthService.login(request);

        assertNotNull(response);
        assertEquals("token123", response.accessToken());
        
        // Ensure it detects the change and pushes update
        verify(adminRepository, times(1)).save(admin);
        assertEquals("DESKTOP|Windows", admin.getDeviceInfo());
    }

    @Test
    void login_whenEmailNotFound_throwsExceptionAndHaltsExecution() {
        when(adminRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidAdminCredentialsException.class, () -> adminAuthService.login(request));
        
        // Ensure we don't leak logic processing CPU arrays
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(adminJwtService);
    }

    @Test
    void login_whenPasswordInvalid_throwsExceptionAndHaltsExecution() {
        when(adminRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("raw_pass", "encoded_pass")).thenReturn(false);

        InvalidAdminCredentialsException exception = assertThrows(InvalidAdminCredentialsException.class, () -> adminAuthService.login(request));
        assertEquals("Invalid email or password.", exception.getMessage());
        
        verifyNoInteractions(adminJwtService);
    }
}
