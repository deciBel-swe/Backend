package software.decibel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_whenUserIsBanned_returnsForbiddenAndDoesNotAuthenticate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/comments/1/report");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        User bannedUser = new User();
        bannedUser.setId(2L);
        bannedUser.setBanned(true);

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractSubject("valid-token")).thenReturn("2");
        when(userRepository.findById(2L)).thenReturn(Optional.of(bannedUser));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertEquals("{\"error\": \"Forbidden\", \"message\": \"Your account is banned\"}", response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(filterChain.getRequest());
    }

    @Test
    void doFilterInternal_whenUserExistsAndIsActive_populatesSecurityContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/comments/1/report");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        User activeUser = new User();
        activeUser.setId(2L);
        activeUser.setUsername("demo_user");
        activeUser.setBanned(false);

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractSubject("valid-token")).thenReturn("2");
        when(userRepository.findById(2L)).thenReturn(Optional.of(activeUser));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertEquals(request, filterChain.getRequest());
        verify(userRepository).findById(2L);
    }
}
