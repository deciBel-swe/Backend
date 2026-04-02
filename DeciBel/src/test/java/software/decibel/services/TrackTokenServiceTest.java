package software.decibel.services;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.dtos.track.TrackTokenResponse;
import software.decibel.entities.Track;
import software.decibel.entities.TrackToken;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.TrackMapper;
import software.decibel.mappers.TrackTokenMapper;
import software.decibel.repositories.TrackTokenRepository;
import software.decibel.services.track.TrackService;
import software.decibel.services.track.TrackTokenService;

class TrackTokenServiceTest {

    @Mock
    private TrackTokenRepository trackTokenRepository;
    @Mock
    private TrackService trackService;
    @Mock
    private TrackTokenMapper trackTokenMapper;
    @Mock
    private TrackMapper trackMapper;

    @InjectMocks
    private TrackTokenService trackTokenService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // getActiveToken
    // -------------------------------
    @Test
    void shouldReturnActiveToken_whenTokenExists() {
        // Arrange
        Long trackId = 1L;
        Track track = new Track();
        TrackToken token = TrackToken.builder().track(track).token("A TOKEN").build();
        TrackTokenResponse tokenResponse = mock(TrackTokenResponse.class);

        // mock track exists
        when(trackService.getTrackIfExistsById(trackId)).thenReturn(track);

        // moke repo returns token
        when(trackTokenRepository.findByTrackIdAndIsDeletedFalse(trackId))
                .thenReturn(Optional.of(token));

        // mock mapper
        when(trackTokenMapper.toTrackTokenResponse(token)).thenReturn(tokenResponse);

        // Act
        TrackTokenResponse result = trackTokenService.getActiveToken(trackId);

        // Assert
        assertEquals(tokenResponse, result);
    }

    @Test
    void shouldThrow_whenNoActiveTokenExists() {
        // Arrange
        Long trackId = 1L;
        Track track = new Track();

        // track exists
        when(trackService.getTrackIfExistsById(trackId)).thenReturn(track);

        // repo returns empty
        when(trackTokenRepository.findByTrackIdAndIsDeletedFalse(trackId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> trackTokenService.getActiveToken(trackId));
    }

    // regenerateToken
    // -------------------------------
    @Test
    void shouldSoftDeleteOldTokenAndCreateNew() {

        // Arrange
        User dummyUser = new User();
        dummyUser.setId(1L);
        // --- SPRING SECURITY MOCK ---
        UserPrincipal mockPrincipal = mock(UserPrincipal.class);
        when(mockPrincipal.getId()).thenReturn(1L);
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(mockPrincipal); // <-- Return the UserPrincipal, not the User
        SecurityContextHolder.setContext(securityContext);

        Long trackId = 1L;
        Track track = new Track();
        track.setUploader(dummyUser); // <-- This is the crucial line that was missing!

        TrackToken oldToken = TrackToken.builder().track(track).token("old").build();
        TrackToken newToken = TrackToken.builder().track(track).token("new").build();
        TrackTokenResponse newTokenResponse = mock(TrackTokenResponse.class);

        // track exists
        when(trackService.getTrackIfExistsById(trackId)).thenReturn(track);

        // old token
        when(trackTokenRepository.findByTrackIdAndIsDeletedFalse(trackId))
                .thenReturn(Optional.of(oldToken));

        // mock repo save new token
        when(trackTokenRepository.save(any(TrackToken.class))).thenReturn(newToken);

        // mapper returns dto response
        when(trackTokenMapper.toTrackTokenResponse(newToken)).thenReturn(newTokenResponse);

        // Act
        TrackTokenResponse result = trackTokenService.regenerateToken(trackId);

        // Assert
        assertEquals(newTokenResponse, result);

        // ensure old token soft deleted
        assertTrue(oldToken.isDeleted());

        // verify repository save called for old token
        verify(trackTokenRepository).save(oldToken);

        // verify new token saved separately
        verify(trackTokenRepository).save(argThat(t -> t != oldToken));
    }
}
