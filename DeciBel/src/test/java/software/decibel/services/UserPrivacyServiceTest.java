package software.decibel.services;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.auth.PrivacyUpdateRequest;
import software.decibel.dtos.auth.PrivacyUpdateResponse;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;
import software.decibel.services.user.UserPrivacyService;
import software.decibel.services.user.UserService;

@ExtendWith(MockitoExtension.class)
class UserPrivacyServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private UserPrivacyService userPrivacyService;

    @Test
    void updateMyPrivacy_updatesPrivacyAndReturnsUpdatedResponse() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        User user = User.builder().id(1L).build();
        user.setPrivate(false);
        user.setShowHistory(true);

        UserPrincipal principal = UserPrincipal.fromUser(user);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userService.getUserIfExistsById(1L)).thenReturn(user);

        PrivacyUpdateResponse response = userPrivacyService.updateMyPrivacy(
                authentication,
                new PrivacyUpdateRequest(true, false)
        );

        assertTrue(user.isPrivate());
        assertFalse(user.isShowHistory());
        assertTrue(response.isPrivate());
        assertFalse(response.showHistory());
        verify(userService).getUserIfExistsById(1L);
    }

    @Test
    void updateMyPrivacy_whenAuthenticationIsNull_throwsUnauthorized() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userPrivacyService.updateMyPrivacy(null, new PrivacyUpdateRequest(true, true))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void updateMyPrivacy_whenAuthenticationIsNotAuthenticated_throwsUnauthorized() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userPrivacyService.updateMyPrivacy(authentication, new PrivacyUpdateRequest(true, true))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void updateMyPrivacy_whenUserNotFound_throwsNotFound() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("99");
        when(userService.getUserIfExistsById(99L)).thenThrow(new software.decibel.exceptions.custom.ResourceNotFoundException("User with id 99 not found"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userPrivacyService.updateMyPrivacy(authentication, new PrivacyUpdateRequest(false, false))
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(userService).getUserIfExistsById(99L);
        verifyNoMoreInteractions(userService);
    }
}
