package software.decibel.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;

import java.util.Collection;
import java.util.Collections;

/**
 * A lightweight, serializable principal object for the SecurityContext.
 * Decouples the JPA User entity from the security layer to avoid lazy-loading
 * issues and accidental entity leaks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPrincipal implements UserDetails {

    private Long id;
    private String username;
    private AccountTier tier;

    public static UserPrincipal fromUser(User user) {
        return UserPrincipal.builder()
                .id(user.getId())
                .username(user.getUsername())
                .tier(user.getTier())
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + tier.name()));
    }

    @Override
    public String getPassword() {
        return null; // Not needed for JWT authentication
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
