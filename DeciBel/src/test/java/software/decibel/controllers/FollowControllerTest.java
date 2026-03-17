package software.decibel.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.services.FollowService;
import software.decibel.services.JwtService;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FollowControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FollowService followService;

    @InjectMocks
    private FollowController followController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(followController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void followUser_returnsOk() throws Exception {
        try (MockedStatic<JwtService> jwtServiceMock = mockStatic(JwtService.class)) {
            jwtServiceMock.when(JwtService::getCurrentUserId).thenReturn(1L);

            mockMvc.perform(post("/users/2/follow"))
                    .andExpect(status().isOk());

            verify(followService).followUser(1L, 2L);
        }
    }

    @Test
    void unfollowUser_returnsNoContent() throws Exception {
        try (MockedStatic<JwtService> jwtServiceMock = mockStatic(JwtService.class)) {
            jwtServiceMock.when(JwtService::getCurrentUserId).thenReturn(1L);

            mockMvc.perform(delete("/users/2/follow"))
                    .andExpect(status().isNoContent());

            verify(followService).unfollowUser(1L, 2L);
        }
    }

    @Test
    void getFollowers_returnsOk() throws Exception {
        UserFollowDto dto = new UserFollowDto(1L, "user", "User", "avatar.png", true);
        Page<UserFollowDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);
        
        when(followService.getFollowers(eq(1L), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/users/1/followers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].username").value("user"));
    }

    @Test
    void getFollowing_returnsOk() throws Exception {
        UserFollowDto dto = new UserFollowDto(2L, "following", "Following", "avatar2.png", true);
        Page<UserFollowDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(followService.getFollowing(eq(1L), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/users/1/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].username").value("following"));
    }
}
