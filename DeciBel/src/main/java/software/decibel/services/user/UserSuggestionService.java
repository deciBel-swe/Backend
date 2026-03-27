package software.decibel.services.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.entities.User;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSuggestionService {

    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    private final UserMapper userMapper;

    // function to get list of suggested users for the current user based on genres
    @Transactional(readOnly = true)
    public List<UserFollowDto> getSuggestedUsers(User currentUser, int limit) {
        Set<String> interests = new HashSet<>();
        
        // add user's favorite genres to interests
        if (currentUser.getFavoriteGenres() != null) {
            interests.addAll(currentUser.getFavoriteGenres());
        }

        // add genres of tracks liked by the user to interests
        interests.addAll(trackRepository.findGenresOfLikedTracksByUserId(currentUser.getId()));

        if (interests.isEmpty()) {
            // return empty if no interests found
            return new ArrayList<>();
        }

        // find users with matching interests through repository
        List<User> suggestedUsers = userRepository.findSuggestedUsersByGenres(
                currentUser.getId(), 
                new ArrayList<>(interests), 
                PageRequest.of(0, limit)
        );

        // map user entities to follow dtos through user mapper
        return suggestedUsers.stream()
                .map(userMapper::toUserFollowDto)
                .collect(Collectors.toList());
    }
}
