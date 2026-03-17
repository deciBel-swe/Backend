package software.decibel.services;

import lombok.RequiredArgsConstructor;
import org.apache.commons.text.WordUtils;
import org.springframework.stereotype.Service;
import software.decibel.entities.Tag;
import software.decibel.repositories.TagRepository;

@Service
@RequiredArgsConstructor
// Note: Tag titles are always saved Title Case and will be searched like that too

public class TagService {

  private final TagRepository tagRepository;

  // Get existing tag or create a new one (title-cased)
  public Tag getOrCreateTag(String title) {
    String formattedTitle = WordUtils.capitalizeFully(title);
    return tagRepository
        .findByTitle(formattedTitle)
        .orElseGet(
            () -> {
              Tag newTag = Tag.builder().title(formattedTitle).build();
              return tagRepository.save(newTag);
            });
  }
}
