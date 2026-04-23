package software.decibel.controllers.User;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import software.decibel.dtos.discovery.StationPageResponse;
import software.decibel.dtos.track.TrackSummaryDTO;
import software.decibel.dtos.user.UserSummary;
import software.decibel.exceptions.GlobalExceptionHandler;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.track.TrackService;
import software.decibel.services.user.UserHistoryService;

@ExtendWith(MockitoExtension.class)
class UserTrackControllerTest {

    @Mock
    private TrackService trackService;
    @Mock
    private LikeService likeService;
    @Mock
    private RepostService repostService;
    @Mock
    private UserHistoryService userHistoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserTrackController controller = new UserTrackController(trackService, likeService, repostService, userHistoryService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getListeningHistory_whenRequestIsValid_returnsHistoryPage() throws Exception {
        TrackSummaryDTO summary = new TrackSummaryDTO(
                15L,
                "Track Title",
                "track-title",
                "cover.jpg",
                "track.mp3",
                new UserSummary(3L, "artist", "Artist Name", "artist.jpg"),
                10,
                4,
                1,
                2,
                true,
                false,
                "secret-token");
        StationPageResponse response = new StationPageResponse(List.of(summary), 0, 20, 1, 1, true);

        when(userHistoryService.getMyListeningHistory(0, 20)).thenReturn(response);

        mockMvc.perform(get("/users/me/history")
                .param("page", "0")
                .param("size", "20")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(15))
                .andExpect(jsonPath("$.content[0].title").value("Track Title"))
                .andExpect(jsonPath("$.content[0].artist.username").value("artist"))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.isLast").value(true));

        verify(userHistoryService).getMyListeningHistory(0, 20);
    }

    @Test
    void getListeningHistory_whenPageIsInvalidType_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/users/me/history")
                .param("page", "not-a-number")
                .param("size", "20"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userHistoryService);
    }
}
