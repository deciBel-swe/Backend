package software.decibel.dtos.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAdminResponse {
    private String accessToken;
    private Long expiresIn;
    private AdminUserResponse adminUser;
}
