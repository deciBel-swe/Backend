package software.decibel.dtos.user;

import java.util.List;

public record UserProfile(
        String bio,
        String city,
        String country,
        String profilePic,
        String coverPic,
        List<String> favoriteGenres) {

}
