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
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

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
                        // Map account tier to Spring Security role (e.g., ROLE_LISTENER)
                        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                                new SimpleGrantedAuthority("ROLE_" + user.getTier().name())
                        );
                        
                        // Populate SecurityContext with full User object as principal
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                authorities
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    } else {
                        log.debug("User with ID {} not found in database", userId);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
}
