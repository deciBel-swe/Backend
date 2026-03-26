package software.decibel.dtos.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user information in blocked lists.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedUserDto {
    private Long id;
    private String username;
    private String displayName;
    private String avatarUrl;
}
