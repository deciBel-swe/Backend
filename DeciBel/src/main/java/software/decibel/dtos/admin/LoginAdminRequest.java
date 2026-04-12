package software.decibel.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.decibel.dtos.auth.DeviceInfo;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAdminRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotNull(message = "Device Info is required")
    private DeviceInfo deviceInfo;
}
