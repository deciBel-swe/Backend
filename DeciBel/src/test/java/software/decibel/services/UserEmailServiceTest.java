package software.decibel.services;

import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.auth.IssuedToken;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.user.ChangeEmailRequest;
import software.decibel.dtos.user.VerifyEmailChangeRequest;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.PendingEmailChange;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.TokenType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.PendingEmailChangeRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.user.UserEmailService;
import software.decibel.services.user.UserService;

@ExtendWith(MockitoExtension.class)
class UserEmailServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;

    @Mock
    private AuthIdentityRepository authIdentityRepository;

    @Mock
    private PendingEmailChangeRepository pendingEmailChangeRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private FrontendLinkService frontendLinkService;

    @InjectMocks
    private UserEmailService userEmailService;

    @Test
    void requestMyEmailChange_whenRequestIsValid_createsPendingRequestAndSendsEmail() {
        Authentication authentication = authenticatedUser("1");
        User user = User.builder().id(1L).username("user").build();
        AuthIdentity currentIdentity = AuthIdentity.builder()
                .user(user)
                .email("old@example.com")
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .build();
        Token token = Token.builder()
                .tokenId(8L)
                .tokenType(TokenType.EMAIL_CHANGE)
                .hash("hashed-token")
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        when(userService.getUserIfExistsById(1L)).thenReturn(user);
        when(authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD))
                .thenReturn(Optional.of(currentIdentity));
        when(authIdentityRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(pendingEmailChangeRepository.existsByNewEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(pendingEmailChangeRepository.findByUser(user)).thenReturn(Optional.empty());
        when(tokenService.createEmailChangeToken(user))
                .thenReturn(new IssuedToken("raw-token", token));
        when(frontendLinkService.buildEmailChangeVerificationLink("raw-token"))
                .thenReturn("https://decibel.foo/verify-email-change?token=raw-token");

        MessageResponse response = userEmailService.requestMyEmailChange(
                authentication,
                new ChangeEmailRequest("new@example.com"));

        assertEquals("Verification email sent successfully", response.message());
        verify(tokenService).deleteTokensForUserAndType(user, TokenType.EMAIL_CHANGE);
        verify(pendingEmailChangeRepository).save(any(PendingEmailChange.class));
        verify(emailService).sendEmailChangeVerificationEmail(
                "new@example.com",
                "https://decibel.foo/verify-email-change?token=raw-token");
    }

    @Test
    void requestMyEmailChange_whenEmailAlreadyExists_throwsConflict() {
        Authentication authentication = authenticatedUser("1");
        User user = User.builder().id(1L).username("user").build();
        AuthIdentity currentIdentity = AuthIdentity.builder()
                .user(user)
                .email("old@example.com")
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .build();

        when(userService.getUserIfExistsById(1L)).thenReturn(user);
        when(authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD))
                .thenReturn(Optional.of(currentIdentity));
        when(authIdentityRepository.existsByEmailIgnoreCase("used@example.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userEmailService.requestMyEmailChange(authentication, new ChangeEmailRequest("used@example.com")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(tokenService, never()).createEmailChangeToken(any(User.class));
    }

    @Test
    void verifyMyEmailChange_whenTokenIsValid_updatesEmailAndMarksTokenUsed() {
        Authentication authentication = authenticatedUser("1");
        User user = User.builder().id(1L).username("user").build();
        Token token = Token.builder()
                .tokenId(8L)
                .user(user)
                .tokenType(TokenType.EMAIL_CHANGE)
                .hash("hashed-token")
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        PendingEmailChange pendingEmailChange = PendingEmailChange.builder()
                .pendingEmailChangeId(3L)
                .user(user)
                .newEmail("new@example.com")
                .token(token)
                .build();
        AuthIdentity identity = AuthIdentity.builder()
                .authId(2L)
                .user(user)
                .email("old@example.com")
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .build();

        when(userService.getUserIfExistsById(1L)).thenReturn(user);
        when(tokenService.findValidUnusedToken("raw-token", TokenType.EMAIL_CHANGE, "Invalid or expired token"))
                .thenReturn(token);
        when(pendingEmailChangeRepository.findByToken(token)).thenReturn(Optional.of(pendingEmailChange));
        when(authIdentityRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(authIdentityRepository.findAllByUser(user)).thenReturn(List.of(identity));

        MessageResponse response = userEmailService.verifyMyEmailChange(
                authentication,
                new VerifyEmailChangeRequest("raw-token"));

        assertEquals("Email changed successfully", response.message());
        assertEquals("new@example.com", identity.getEmail());
        verify(userRepository, never()).save(any(User.class));
        verify(authIdentityRepository).saveAll(List.of(identity));
        verify(pendingEmailChangeRepository).delete(pendingEmailChange);
        verify(tokenService).markTokenUsed(token);
    }

    @Test
    void verifyMyEmailChange_whenPendingRequestBelongsToAnotherUser_throwsForbidden() {
        Authentication authentication = authenticatedUser("1");
        User currentUser = User.builder().id(1L).username("user").build();
        User anotherUser = User.builder().id(2L).username("another").build();
        Token token = Token.builder()
                .tokenId(8L)
                .user(anotherUser)
                .tokenType(TokenType.EMAIL_CHANGE)
                .hash("hashed-token")
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        PendingEmailChange pendingEmailChange = PendingEmailChange.builder()
                .user(anotherUser)
                .newEmail("new@example.com")
                .token(token)
                .build();

        when(userService.getUserIfExistsById(1L)).thenReturn(currentUser);
        when(tokenService.findValidUnusedToken("raw-token", TokenType.EMAIL_CHANGE, "Invalid or expired token"))
                .thenReturn(token);
        when(pendingEmailChangeRepository.findByToken(token)).thenReturn(Optional.of(pendingEmailChange));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userEmailService.verifyMyEmailChange(authentication, new VerifyEmailChangeRequest("raw-token")));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
    }

    private Authentication authenticatedUser(String userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId);
        return authentication;
    }
}
