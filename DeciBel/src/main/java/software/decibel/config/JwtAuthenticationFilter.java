package software.decibel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        // Tell the filter to skip any route starting with /auth/
        return path.startsWith("/auth/");
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
                        sendErrorResponse(response, "User not found");
                        return;
                    }
                }
            } else {
                log.debug("Invalid JWT token provided");
                sendErrorResponse(response, "Invalid token");
                return;
            }
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
            sendErrorResponse(response, "Authentication failed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + message + "\"}");
    }
}
