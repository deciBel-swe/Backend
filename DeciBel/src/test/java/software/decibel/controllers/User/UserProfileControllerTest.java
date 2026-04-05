package software.decibel.controllers.User;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.dtos.user.UserProfile;
import software.decibel.services.user.UserProfileService;
import software.decibel.services.user.UserProfileTokenService;

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
        String displayname = "Test User";
        UserProfile profile = new UserProfile(
                2L,
                "test@example.com",
                username,
                displayname, // displayName
                null, // AccountTier
                10, // followerCount
                5, // followingCount
                2, // trackCount
                true, // isFollowed
                false,// isFollowing
                false,// isBlocked
                "My Bio",
                "London",
                "UK",
                "avatar.jpg",
                "cover.jpg",
                List.of("Rock", "Jazz"),
                List.of() // socialLinksDto
        );
        UpdateProfileResponse response = new UpdateProfileResponse(
                profile,
                null // privacySettings
        );

        when(userService.getUserPublicProfileByUsername(username, null)).thenReturn(response);

        mockMvc.perform(get("/users/username/" + username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.username").value(username))
                .andExpect(jsonPath("$.profile.isFollowed").value(true))
                .andExpect(jsonPath("$.profile.isFollowing").value(false))
                .andExpect(jsonPath("$.profile.isBlocked").value(false));

        verify(userService).getUserPublicProfileByUsername(username, null);
    }
}
