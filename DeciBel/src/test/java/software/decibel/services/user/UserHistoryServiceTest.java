package software.decibel.services.user;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import software.decibel.dtos.discovery.StationPageResponse;
import software.decibel.entities.ListeningHistory;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.mappers.StationMapper;
import software.decibel.repositories.ListeningHistoryRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.TrackTokenRepository;
import software.decibel.services.JwtService;

class UserHistoryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private ListeningHistoryRepository listeningHistoryRepository;
    @Mock
    private TrackLikeRepository trackLikeRepository;
    @Mock
    private TrackRepostRepository trackRepostRepository;
    @Mock
    private TrackTokenRepository trackTokenRepository;
    @Mock
    private StationMapper stationMapper;
    @Mock
    private UserService userService;

    @InjectMocks
    private UserHistoryService userHistoryService;

    private MockedStatic<JwtService> jwtMock;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jwtMock = mockStatic(JwtService.class);
        jwtMock.when(JwtService::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        jwtMock.close();
    }

    @Test
    void getMyListeningHistory_returnsMappedPageWhenHistoryExists() {
        User user = User.builder().id(USER_ID).build();
        Track track = Track.builder().id(15L).build();
        ListeningHistory item = ListeningHistory.builder().user(user).track(track).build();
        Page<ListeningHistory> historyPage = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
        Set<Long> likedIds = Set.of(15L);
        Set<Long> repostedIds = Set.of();
        Map<Long, String> tokenMap = Map.of(15L, "secret-token");
        StationPageResponse expected = mock(StationPageResponse.class);

        when(userService.getUserIfExistsById(USER_ID)).thenReturn(user);
        when(listeningHistoryRepository.findByUserIdOrderByPlayedAtDesc(eq(USER_ID), any(PageRequest.class)))
                .thenReturn(historyPage);
        when(trackLikeRepository.findTrackIdsByUserId(USER_ID)).thenReturn(likedIds);
        when(trackRepostRepository.findTrackIdsByUserId(USER_ID)).thenReturn(repostedIds);
        when(trackTokenRepository.findActiveTokensByTrackIds(Set.of(15L))).thenReturn(tokenMap);
        when(stationMapper.toPageResponse(any(Page.class), eq(likedIds), eq(repostedIds), eq(tokenMap)))
                .thenReturn(expected);

        StationPageResponse result = userHistoryService.getMyListeningHistory(0, 20);

        assertSame(expected, result);
        verify(userService).getUserIfExistsById(USER_ID);
        verify(listeningHistoryRepository).findByUserIdOrderByPlayedAtDesc(eq(USER_ID), any(PageRequest.class));
        verify(trackTokenRepository).findActiveTokensByTrackIds(Set.of(15L));
    }

    @Test
    void getMyListeningHistory_skipsTokenLookupWhenHistoryIsEmpty() {
        User user = User.builder().id(USER_ID).build();
        Page<ListeningHistory> historyPage = Page.empty(PageRequest.of(0, 20));
        StationPageResponse expected = mock(StationPageResponse.class);

        when(userService.getUserIfExistsById(USER_ID)).thenReturn(user);
        when(listeningHistoryRepository.findByUserIdOrderByPlayedAtDesc(eq(USER_ID), any(PageRequest.class)))
                .thenReturn(historyPage);
        when(trackLikeRepository.findTrackIdsByUserId(USER_ID)).thenReturn(Set.of());
        when(trackRepostRepository.findTrackIdsByUserId(USER_ID)).thenReturn(Set.of());
        when(stationMapper.toPageResponse(any(Page.class), eq(Set.of()), eq(Set.of()), eq(Map.of())))
                .thenReturn(expected);

        StationPageResponse result = userHistoryService.getMyListeningHistory(0, 20);

        assertSame(expected, result);
        verify(trackTokenRepository, never()).findActiveTokensByTrackIds(any());
    }
}
