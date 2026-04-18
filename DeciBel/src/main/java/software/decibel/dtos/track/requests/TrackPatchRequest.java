package software.decibel.dtos.track.requests;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.customValidation.ValidImageFile;
import software.decibel.customValidation.ValidTagList;
import software.decibel.enums.TrackAccess;

public record TrackPatchRequest(
    @Size(max = 200, message = "Title must be less than 200 characters") String title,
    @Size(max = 100, message = "Genre must be less than 100 characters") String genre,
    @Size(max = 2000, message = "Description must be less than 2000 characters") String description,
    Boolean isPrivate,
    // Tags are sent as a JSON string (e.g. '["rock","pop"]') rather than a repeated
    // multipart field because multipart/form-data has no  array type. Each repeated
    // field counts as a separate part, which exceeds Tomcat's part limit quickly. Parsing
    // the JSON string on the backend keeps the part count low.
    @ValidTagList String tags,
    @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate releaseDate,
    @ValidImageFile MultipartFile coverImage,
    TrackAccess access) {}
