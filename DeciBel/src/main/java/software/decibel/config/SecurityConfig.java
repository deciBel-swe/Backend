package software.decibel.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AdminJwtAuthenticationFilter adminJwtAuthFilter;

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF for stateless API
                .csrf(AbstractHttpConfigurer::disable)
                // Configure CORS using the source defined below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Define endpoint accessibility: /auth/** is public, all others require authentication
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/auth/**").permitAll()
                                .requestMatchers("/admin/login").permitAll()
                                .requestMatchers("/ws/**").permitAll()
                                .requestMatchers("/oauth/**").permitAll()
                                .requestMatchers("/login/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/*/followers").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/*/following").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/{userId}").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/username/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/profile/token/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/playlists/{playlistId}").permitAll()
                                .requestMatchers(HttpMethod.POST, "/auth/refreshtoken").permitAll()
                                .requestMatchers(HttpMethod.POST, "/tracks/upload").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/{userId}/tracks").permitAll()
                                .requestMatchers(HttpMethod.GET, "/playlists/token/{token}").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/tracks/{trackId}/like").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/tracks/{trackId}/reposters").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/playlists/{playlistId}/like").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/playlists/{playlistId}/reposters").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/{username}/playlists").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/{username}/tracks").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/{username}/followers").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/{username}/following").permitAll()
                                .requestMatchers(HttpMethod.GET, "/tracks/{trackId}/comments").permitAll()
                                .requestMatchers(HttpMethod.GET, "/explore/trending").permitAll()
                                .requestMatchers(HttpMethod.GET, "/comments/{commentId}/replies").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/{username}/liked-tracks").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users/{username}/reposted-tracks").permitAll()
                                .requestMatchers("/webhook/**").permitAll()
                                //needed for api docs
                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                                .anyRequest().authenticated())
                // Use stateless sessions for JWT authentication
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Register JWT filter before the standard authentication filter
                .addFilterBefore(adminJwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Harden security headers
                .headers(
                        headers
                        -> headers
                                .frameOptions(frameOptions -> frameOptions.deny())
                                // Disable XSS protection header for modern browsers which use CSP instead
                                .xssProtection(xss -> xss.disable())
                                .contentSecurityPolicy(
                                        csp
                                        -> csp.policyDirectives(
                                                "default-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; connect-src 'self' ws: wss:;"))
                                .permissionsPolicyHeader(
                                        permissions
                                        -> permissions.policy("geolocation=(), microphone=(), camera=()")))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Browsers reject wildcard origins when credentials are allowed.
        // We use a property to define allowed origins, falling back to wildcard for local dev only if
        // needed.
        if ("*".equals(allowedOrigins)) {
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        }

        // Standard HTTP methods allowed for the frontend
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Required headers for authentication and JSON requests
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        // Allow sending credentials (cookies, auth headers)
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply CORS settings to all paths
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
