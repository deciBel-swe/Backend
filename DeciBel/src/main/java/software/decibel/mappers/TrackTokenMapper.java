package software.decibel.mappers;

import org.mapstruct.*;
import software.decibel.dtos.track.TrackTokenResponse;
import software.decibel.entities.TrackToken;

@Mapper(componentModel = "spring") // Spring injects it as a @Component
public interface TrackTokenMapper {

  // ----------------- TrackToken DTOs ---------------------

  // TrackToken -> TrackTokenResponse DTO
  @Mapping(source = "token", target = "secretToken")
  TrackTokenResponse toTrackTokenResponse(TrackToken trackToken);
}
