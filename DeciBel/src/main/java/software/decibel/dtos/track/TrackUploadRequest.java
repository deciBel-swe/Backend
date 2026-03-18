package software.decibel.dtos.track;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.customValidation.ValidAudioFile;
import software.decibel.customValidation.ValidImageFile;
import software.decibel.customValidation.ValidWaveFormData;

public record TrackUploadRequest(
    @ValidAudioFile MultipartFile audioFile,
    @ValidImageFile MultipartFile coverImage,
    @ValidWaveFormData String waveformData,
    @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be less than 200 characters")
        String title,
    @NotBlank(message = "Genre is required")
        @Size(max = 100, message = "Genre must be less than 100 characters")
        String genre,
    @Size(max = 2000, message = "Description must be less than 2000 characters") String description,
    @NotNull(message = "isPrivate flag is required") Boolean isPrivate,
    @NotNull(message = "Release date is required") @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate releaseDate) {}
