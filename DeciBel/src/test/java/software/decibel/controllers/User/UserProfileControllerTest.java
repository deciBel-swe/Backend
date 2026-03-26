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
import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserProfileService;
import software.decibel.services.user.UserProfileTokenService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserProfileService userService;

    @Mock
    private UserProfileTokenService userProfileTokenService;

    @InjectMocks
    private UserProfileController userProfileController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userProfileController).build();
    }

    @Test
    void getUserProfileByUsername_returnsFollowStatus() throws Exception {
        String username = "testuser";
        UpdateProfileResponse response = new UpdateProfileResponse(
                2L, "test@example.com", username, true, null, 10, 5, 2, true, false, null, null, null
        );

        when(userService.getUserPublicProfileByUsername(username)).thenReturn(response);

        mockMvc.perform(get("/users/username/" + username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.isFollowed").value(true))
                .andExpect(jsonPath("$.isFollowing").value(false));

        verify(userService).getUserPublicProfileByUsername(username);
    }
}
