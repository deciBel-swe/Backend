package software.decibel.dtos.user;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 500, message = "Bio must be at most 500 characters")
        String bio,
        @Size(max = 100, message = "City must be at most 100 characters")
        String city,
        @Size(max = 100, message = "Country must be at most 100 characters")
        String country,
        @Valid
        @Size(max = 10, message = "Favorite genres must be at most 10 items")
        List<@Size(max = 20, message = "Each genre must not exceed 20 characters") String> favoriteGenres,
        @Valid
        SocialLinksDto socialLinks
        ) {

}
