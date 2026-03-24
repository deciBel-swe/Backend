package software.decibel.dtos.user;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 500, message = "Bio must be at most 500 characters")
        String bio,
        @Size(max = 100, message = "City must be at most 36 characters")
        String city,
        @Size(max = 100, message = "Country must be at most 36 characters")
        String country,
        @Size(max = 10, message = "Favorite genres must be at most 10 items")
        List<@Size(max = 100, message = "Each favorite genre must be at most 36 characters") String> favoriteGenres,
        @Valid
        SocialLinksDto socialLinksDto
        ) {

}
