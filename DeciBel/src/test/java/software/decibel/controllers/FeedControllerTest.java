package software.decibel.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.decibel.dtos.discovery.FeedPageResponse;
import software.decibel.entities.User;
import software.decibel.services.FeedService;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserService;

import java.util.Collections;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FeedService feedService;

    @Mock
    private UserService userService;

    @InjectMocks
    private FeedController feedController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(feedController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getFeed_unauthorized_returns401() throws Exception {
        try (MockedStatic<JwtService> jwtServiceMock = mockStatic(JwtService.class)) {
            jwtServiceMock.when(JwtService::getCurrentUserId).thenReturn(null);

            mockMvc.perform(get("/feed"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void getFeed_authorized_returnsOk() throws Exception {
        try (MockedStatic<JwtService> jwtServiceMock = mockStatic(JwtService.class)) {
            jwtServiceMock.when(JwtService::getCurrentUserId).thenReturn(1L);

            User user = new User();
            user.setId(1L);
            when(userService.getUserIfExistsById(1L)).thenReturn(user);

            FeedPageResponse response = new FeedPageResponse(Collections.emptyList(), 0, 10, 0, 0, true);
            when(feedService.getFeed(eq(user), any(Pageable.class))).thenReturn(response);

            mockMvc.perform(get("/feed"))
                    .andExpect(status().isOk());
        }
    }
}
