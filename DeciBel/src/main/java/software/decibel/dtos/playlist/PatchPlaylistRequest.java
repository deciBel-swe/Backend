package software.decibel.dtos.playlist;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Size;
import software.decibel.customValidation.ValidImageFile;
import software.decibel.enums.PlaylistType;

public record PatchPlaylistRequest(
        @Size(max = 100, message = "title must not be more than 100 characters")
        String title,
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,
        PlaylistType type,
        Boolean isPrivate,
        @ValidImageFile
        MultipartFile coverArt
        ) {

}
