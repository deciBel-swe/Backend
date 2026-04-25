package software.decibel.services;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.user.ChangePasswordRequest;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.TokenType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.services.user.UserPasswordService;
import software.decibel.services.user.UserService;

@ExtendWith(MockitoExtension.class)
class UserPasswordServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthIdentityRepository authIdentityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionService sessionService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private UserPasswordService userPasswordService;

    @Test
    void resetMyPassword_whenCurrentPasswordMatches_updatesPasswordAndDeletesRefreshTokens() {
        Authentication authentication = authenticatedUser("1");
        User user = User.builder().id(1L).username("user").build();
        AuthIdentity authIdentity = AuthIdentity.builder()
                .user(user)
                .passwordHash("hashed-old")
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .build();

        when(userService.getUserIfExistsByUsername("1")).thenReturn(user);
        when(authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD))
                .thenReturn(Optional.of(authIdentity));
        when(passwordEncoder.matches("currentPass123!", "hashed-old")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123!", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("hashed-new");

        MessageResponse response = userPasswordService.resetMyPassword(
                authentication,
                new ChangePasswordRequest("currentPass123!", "NewPass123!")
        );

        assertEquals("Password changed successfully", response.message());
        assertEquals("hashed-new", authIdentity.getPasswordHash());
        verify(authIdentityRepository).save(authIdentity);
        verify(sessionService).deleteAllSessionsForUser(user);
        verify(tokenService).deleteTokensForUserAndType(user, TokenType.REFRESH_TOKEN);
    }

    @Test
    void resetMyPassword_whenCurrentPasswordIncorrect_throwsBadRequest() {
        Authentication authentication = authenticatedUser("1");
        User user = User.builder().id(1L).username("user").build();
        AuthIdentity authIdentity = AuthIdentity.builder()
                .user(user)
                .passwordHash("hashed-old")
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .build();

        when(userService.getUserIfExistsByUsername("1")).thenReturn(user);
        when(authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD))
                .thenReturn(Optional.of(authIdentity));
        when(passwordEncoder.matches("wrong-pass", "hashed-old")).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userPasswordService.resetMyPassword(
                        authentication,
                        new ChangePasswordRequest("wrong-pass", "NewPass123!")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(tokenService, never()).deleteTokensForUserAndType(any(User.class), any(TokenType.class));
    }

    @Test
    void resetMyPassword_whenNewPasswordMatchesCurrent_throwsBadRequest() {
        Authentication authentication = authenticatedUser("1");
        User user = User.builder().id(1L).username("user").build();
        AuthIdentity authIdentity = AuthIdentity.builder()
                .user(user)
                .passwordHash("hashed-old")
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .build();

        when(userService.getUserIfExistsByUsername("1")).thenReturn(user);
        when(authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD))
                .thenReturn(Optional.of(authIdentity));
        when(passwordEncoder.matches("currentPass123!", "hashed-old")).thenReturn(true);
        when(passwordEncoder.matches("currentPass123!", "hashed-old")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userPasswordService.resetMyPassword(
                        authentication,
                        new ChangePasswordRequest("currentPass123!", "currentPass123!")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(tokenService, never()).deleteTokensForUserAndType(any(User.class), any(TokenType.class));
    }

    private Authentication authenticatedUser(String userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId);
        return authentication;
    }
}
