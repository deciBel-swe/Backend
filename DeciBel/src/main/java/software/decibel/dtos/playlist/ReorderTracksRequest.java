package software.decibel.dtos.playlist;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReorderTracksRequest(
        @NotNull(message = "Track IDs must not be null")
        List<Long> trackIds
        ) {

}
