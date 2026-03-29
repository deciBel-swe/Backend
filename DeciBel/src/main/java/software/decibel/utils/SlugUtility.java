package software.decibel.utils;

import java.text.Normalizer;
import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.decibel.repositories.PlaylistRepository;

@Component
@RequiredArgsConstructor
public class SlugUtility {

    private final PlaylistRepository playlistRepository;

    // Generates a unique slug from a title
    // ex: "My Playlist" -> "my-playlist" or "my-playlist-a1b2" if taken
    public String generateUniqueSlug(String title) {
        String base = toSlug(title);
        String slug = base;

        // Append short UUID suffix if slug is taken
        while (playlistRepository.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 4);
        }

        return slug;
    }

    private String toSlug(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }

}
