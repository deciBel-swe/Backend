package software.decibel.services;

import software.decibel.dtos.PrivacyUpdateRequest;
import software.decibel.dtos.PrivacyUpdateResponse;
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPrivacyServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserPrivacyService userPrivacyService;

    @Test
    void updateMyPrivacy_updatesPrivacyAndReturnsUpdatedResponse() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("1");

        User user = User.builder().id(1L).build();
        user.setPrivate(false);
        user.setShowHistory(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        PrivacyUpdateResponse response = userPrivacyService.updateMyPrivacy(
                authentication,
                new PrivacyUpdateRequest(true, false)
        );

        assertTrue(user.isPrivate());
        assertFalse(user.isShowHistory());
        assertTrue(response.isPrivate());
        assertFalse(response.showHistory());
        verify(userRepository).findById(1L);
        verifyNoMoreInteractions(userRepository);
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
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userPrivacyService.updateMyPrivacy(authentication, new PrivacyUpdateRequest(false, false))
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(userRepository).findById(99L);
        verifyNoMoreInteractions(userRepository);
    }
}
