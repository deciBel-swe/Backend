package software.decibel.dtos.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.enums.AccountTier;

public record TierUpgradeRequest(
        @NotNull
        AccountTier targetTier,
        @NotNull
        @Valid
        DeviceInfo deviceInfo
        ) {

}
