package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.SocialLinks;
import software.decibel.entities.User;
import software.decibel.enums.SocialPlatform;
import java.util.List;
import java.util.Optional;

public interface SocialLinksRepository extends JpaRepository<SocialLinks, Long> {

    List<SocialLinks> findAllByUser(User user);
    Optional<SocialLinks> findByUserAndPlatform(User user, SocialPlatform platform);
    

}
