package software.decibel.config;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        // Tell the filter to skip any route starting with /auth/ or /admin
        return path.startsWith("/auth/") || path.startsWith("/admin") || path.startsWith("/ws");

    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userId;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Skip filter if no valid Authorization header is found
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7); // Extract token from "Bearer <token>"
        try {
            if (jwtService.isTokenValid(jwt)) {

                userId = jwtService.extractSubject(jwt);

                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long id = Long.parseLong(userId);

                    User user = userRepository.findById(id).orElse(null);

                    if (user != null) {
                        if (Boolean.TRUE.equals(user.isBanned())) {
                            log.debug("Blocked authentication for banned user with ID {}", userId);
                            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Your account is banned");
                            return;
                        }

                        // Create lightweight principal instead of using JPA entity
                        UserPrincipal principal = UserPrincipal.fromUser(user);

                        // Populate SecurityContext with UserPrincipal object as principal
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    } else {
                        log.debug("User with ID {} not found in database", userId);
                        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "User not found");
                        return;
                    }
                }
            } else {
                log.debug("Invalid JWT token provided");
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        String error = status == HttpServletResponse.SC_FORBIDDEN ? "Forbidden" : "Unauthorized";
        response.getWriter().write("{\"error\": \"" + error + "\", \"message\": \"" + message + "\"}");
    }
}
