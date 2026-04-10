package software.decibel.services.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.entities.User;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.FollowRepository;
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
    private final FollowRepository followRepository;
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

        List<User> suggestedUsers;
        if (interests.isEmpty()) {
            // Suggest popular users if no interests are found
            suggestedUsers = userRepository.findPopularUsers(
                    currentUser.getId(),
                    PageRequest.of(0, limit)
            );
        } else {
            // find users with matching interests through repository
            suggestedUsers = userRepository.findSuggestedUsersByGenres(
                    currentUser.getId(), 
                    new ArrayList<>(interests), 
                    PageRequest.of(0, limit)
            );
        }

        // map user entities to follow dtos through user mapper
        return suggestedUsers.stream()
                .filter(user -> !user.getId().equals(currentUser.getId())) // Extra safety to exclude current user
                .map(user -> {
                    UserFollowDto dto = userMapper.toUserFollowDto(user);
                    // check if current user is following the suggested user
                    boolean isFollowing = followRepository.existsByFollowerAndFollowing(currentUser, user);
                    return dto.toBuilder().isFollowing(isFollowing).build();
                })
                .collect(Collectors.toList());
    }
}
