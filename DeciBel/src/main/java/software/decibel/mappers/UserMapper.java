package software.decibel.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import software.decibel.dtos.user.BlockedUserDto;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.dtos.user.UserProfile;
import software.decibel.entities.User;
import software.decibel.utils.UserMappingUtility;

// Mapper to convert User entity to DTOs
@Mapper(componentModel = "spring", uses = UserMappingUtility.class)
public interface UserMapper {

    // Converts User entity to UserFollowDto, ignoring isFollowing status (handled by the service)
    @Mapping(target = "isFollowing", ignore = true)
    UserFollowDto toUserFollowDto(User user);

    // Converts User entity to BlockedUserDto
    BlockedUserDto toBlockedUserDto(User user);

    default UserProfile toUserProfile(User target, User currentViewer,
            UserMappingUtility userMappingUtility,
            software.decibel.repositories.FollowRepository followRepository,
            software.decibel.repositories.BlockRepository blockRepository) {

        boolean isFollowed = false;
        boolean isFollowing = false;
        boolean isBlocked = false;

        if (currentViewer != null && !currentViewer.getId().equals(target.getId())) {
            isFollowed = followRepository.existsByFollowerAndFollowing(currentViewer, target);
            isFollowing = followRepository.existsByFollowerAndFollowing(target, currentViewer);
            isBlocked = blockRepository.existsByBlockerAndBlocked(currentViewer, target);
        }

        return userMappingUtility.toUserProfile(target, isFollowed, isFollowing, isBlocked);
    }
}
