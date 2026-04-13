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
import software.decibel.dtos.auth.AdminPrincipal;
import software.decibel.entities.Admin;
import software.decibel.repositories.AdminRepository;
import software.decibel.services.AdminJwtService;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private final AdminJwtService adminJwtService;
    private final AdminRepository adminRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/admin") || path.equals("/admin/login");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String adminId;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            if (adminJwtService.isTokenValid(jwt)) {
                adminId = adminJwtService.extractSubject(jwt);

                if (adminId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long id = Long.parseLong(adminId);
                    Admin admin = adminRepository.findById(id).orElse(null);

                    if (admin != null) {
                        AdminPrincipal principal = AdminPrincipal.fromAdmin(admin);

                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    } else {
                        log.debug("Admin with ID {} not found", adminId);
                        sendErrorResponse(response, "Admin not found");
                        return;
                    }
                }
            } else {
                log.debug("Invalid Admin JWT token");
                sendErrorResponse(response, "Invalid admin token");
                return;
            }
        } catch (Exception e) {
            log.debug("Admin JWT auth failed: {}", e.getMessage());
            sendErrorResponse(response, "Admin auth failed");
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
