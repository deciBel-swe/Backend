package software.decibel.controllers.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserProfileService;
import software.decibel.services.user.UserProfileTokenService;
import software.decibel.services.user.UserSuggestionService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserSuggestionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserSuggestionService userSuggestionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private UserProfileTokenService userProfileTokenService;

    @InjectMocks
    private UserProfileController userProfileController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userProfileController).build();
    }

    @Test
    void getSuggestedUsers_returnsOk() throws Exception {
        User currentUser = User.builder().id(1L).username("current").build();
        UserFollowDto suggestion = UserFollowDto.builder()
                .id(2L)
                .username("suggested")
                .displayName("Suggested")
                .avatarUrl("avatar.png")
                .isFollowing(false)
                .build();

        try (MockedStatic<JwtService> jwtServiceMock = mockStatic(JwtService.class)) {
            jwtServiceMock.when(JwtService::getCurrentUserId).thenReturn(1L);
            when(userRepository.getReferenceById(1L)).thenReturn(currentUser);
            when(userSuggestionService.getSuggestedUsers(any(User.class), anyInt())).thenReturn(List.of(suggestion));

            mockMvc.perform(get("/users/suggested")
                            .param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(2))
                    .andExpect(jsonPath("$[0].username").value("suggested"))
                    .andExpect(jsonPath("$[0].isFollowing").value(false));
        }
    }
}
