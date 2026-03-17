package software.decibel.dtos.track;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.customValidation.ValidImageFile;

public record TrackPatchRequest(
    @Size(max = 200, message = "Title must be less than 200 characters") String title,
    @Size(max = 100, message = "Genre must be less than 100 characters") String genre,
    @Size(max = 2000, message = "Description must be less than 2000 characters") String description,
    Boolean isPrivate,
    List<String> tags,
    @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate releaseDate,
    @ValidImageFile MultipartFile coverImage) {}
