package software.decibel.dtos.playlist;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import software.decibel.customValidation.ValidImageFile;
import software.decibel.enums.PlaylistType;

public record CreatePlaylistRequest(
        @NotBlank(message = "Title must be not blank")
        @Size(max = 100, message = "Title must not exceed 100 characters")
        String title,
        @Size(max = 500, message = "description must not exceed 500 characters")
        String description,
        @NotNull(message = "Type must not be null")
        PlaylistType type,
        boolean isPrivate,
        @ValidImageFile
        MultipartFile coverArt
        ) {

}
