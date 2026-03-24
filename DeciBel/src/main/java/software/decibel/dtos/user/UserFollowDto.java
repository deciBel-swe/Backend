package software.decibel.dtos.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO for user information in follow lists
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFollowDto {
    private Long id;
    private String username;
    private String displayName;
    private String avatarUrl;
    // Indicates if the current viewer is following this user
    private boolean isFollowing;
}
