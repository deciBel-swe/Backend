package software.decibel.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
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
import software.decibel.dtos.user.BlockedUserDto;
import software.decibel.services.BlockService;
import software.decibel.services.JwtService;

@ExtendWith(MockitoExtension.class)
class BlockControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BlockService blockService;

    @InjectMocks
    private BlockController blockController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(blockController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void blockUser_returnsOk() throws Exception {
        try (MockedStatic<JwtService> jwtServiceMock = mockStatic(JwtService.class)) {
            jwtServiceMock.when(JwtService::getCurrentUserId).thenReturn(1L);

            mockMvc.perform(post("/api/users/2/block"))
                    .andExpect(status().isOk());

            verify(blockService).blockUser(1L, 2L);
        }
    }

    @Test
    void unblockUser_returnsNoContent() throws Exception {
        try (MockedStatic<JwtService> jwtServiceMock = mockStatic(JwtService.class)) {
            jwtServiceMock.when(JwtService::getCurrentUserId).thenReturn(1L);

            mockMvc.perform(delete("/api/users/2/block"))
                    .andExpect(status().isNoContent());

            verify(blockService).unblockUser(1L, 2L);
        }
    }

    @Test
    void getBlockedUsers_returnsOk() throws Exception {
        try (MockedStatic<JwtService> jwtServiceMock = mockStatic(JwtService.class)) {
            jwtServiceMock.when(JwtService::getCurrentUserId).thenReturn(1L);

            BlockedUserDto dto = new BlockedUserDto(2L, "blocked", "Blocked", "avatar.png");
            Page<BlockedUserDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

            when(blockService.getBlockedUsers(eq(1L), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/users/me/blocked"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(2))
                    .andExpect(jsonPath("$.content[0].username").value("blocked"));
        }
    }
}
