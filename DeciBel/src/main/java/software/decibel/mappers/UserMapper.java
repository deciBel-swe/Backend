package software.decibel.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.entities.User;

// Mapper to convert User entity to UserFollowDto
@Mapper(componentModel = "spring")
public interface UserMapper {

    // Converts User entity to UserFollowDto, ignoring isFollowing status (handled by the service)
    @Mapping(target = "isFollowing", ignore = true)
    UserFollowDto toUserFollowDto(User user);
}
