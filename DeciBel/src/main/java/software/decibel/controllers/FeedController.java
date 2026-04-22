package software.decibel.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.discovery.FeedPageResponse;
import software.decibel.entities.User;
import software.decibel.services.FeedService;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserService;

@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<FeedPageResponse> getFeed(Pageable pageable) {
        Long currentUserId = JwtService.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        User currentUser = userService.getUserIfExistsById(currentUserId);

        return ResponseEntity.ok(feedService.getFeed(currentUser, pageable));
    }
}
