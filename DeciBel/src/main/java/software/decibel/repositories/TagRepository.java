package software.decibel.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {
  Optional<Tag> findByTitle(String title);
}
