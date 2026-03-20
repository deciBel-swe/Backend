package software.decibel.dtos.user;

import java.util.List;

import software.decibel.entities.SocialLinks;

public record UpdateProfileRequest(
        String bio,
        String city,
        String country,
        List<String> favoriteGenres,
        SocialLinks socialLinks
        ) {

}
