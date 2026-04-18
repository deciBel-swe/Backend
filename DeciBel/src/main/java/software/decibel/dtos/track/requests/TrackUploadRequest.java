package software.decibel.dtos.track.requests;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import software.decibel.customValidation.ValidAudioFile;
import software.decibel.customValidation.ValidImageFile;
import software.decibel.customValidation.ValidTagList;
import software.decibel.customValidation.ValidWaveFormData;
import software.decibel.enums.TrackAccess;

public record TrackUploadRequest(
        @ValidAudioFile
        MultipartFile audioFile,
        @ValidImageFile
        MultipartFile coverImage,
        @ValidWaveFormData
        String waveformData,
        // Tags are sent as a JSON string (e.g. '["rock","pop"]') rather than a repeated
        // multipart field because multipart/form-data has no  array type. Each repeated
        // field counts as a separate part, which exceeds Tomcat's part limit quickly. Parsing
        // the JSON string on the backend keeps the part count low.
        @ValidTagList
        String tags,
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be less than 200 characters")
        String title,
        @NotBlank(message = "Genre is required")
        @Size(max = 100, message = "Genre must be less than 100 characters")
        String genre,
        @Size(max = 2000, message = "Description must be less than 2000 characters")
        String description,
        @NotNull(message = "isPrivate flag is required")
        Boolean isPrivate,
        @NotNull(message = "Release date is required")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate releaseDate,
        @NotNull(message = "Track Access is required: PLAYABLE, BLOCKED, PREVIEW")
        TrackAccess access,
        @NotBlank(message = "Upload ID is required for progress tracking")
        String uploadId) {

}
