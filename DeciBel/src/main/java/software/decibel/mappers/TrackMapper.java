package software.decibel.mappers;

import org.mapstruct.*;
import software.decibel.dtos.track.TrackStatusResponse;
import software.decibel.dtos.track.TrackUploadRequest;
import software.decibel.dtos.track.TrackUploadResponse;
import software.decibel.entities.Track;
import software.decibel.entities.User;

@Mapper(componentModel = "spring") // Spring injects it as a @Component
public interface TrackMapper {

  // ----------------- TrackUpload DTOs ---------------------

  // Track -> TrackUploadResponse DTO
  TrackUploadResponse toTrackUploadResponse(Track track);

  // TrackUploadRequest DTO → Track
  // some fields are ignored (will be handled in future iterations), and other fields (trackUrl,
  // coverUrl, durationSeconds) will be computed after mapping
  @Mapping(target = "trackUrl", ignore = true)
  @Mapping(target = "coverUrl", ignore = true)
  @Mapping(target = "durationSeconds", ignore = true)
  @Mapping(target = "tags", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(
      target = "visibility",
      expression =
          "java(dto.isPrivate() ? software.decibel.enums.Visibility.PRIVATE : software.decibel.enums.Visibility.PUBLIC)")
  @Mapping(target = "uploader", source = "uploader")
  Track toEntity(TrackUploadRequest dto, User uploader);

  // ----------------- TrackStatus DTOs ---------------------

  // Track -> TrackStatusResponse DTO
  @Mapping(source = "id", target = "trackId")
  TrackStatusResponse toTrackStatusResponse(Track track);
}
