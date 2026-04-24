package software.decibel.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import software.decibel.dtos.admin.BannedUsersPageResponse;
import software.decibel.dtos.admin.BannedUserResponse;
import software.decibel.entities.User;

@Mapper(componentModel = "spring")
public interface AdminUserMapper {

    @Mapping(target = "isBanned", expression = "java(user.isBanned())")
    BannedUserResponse toBannedUserResponse(User user);

    default BannedUsersPageResponse toBannedUsersPageResponse(
            List<BannedUserResponse> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages,
            boolean isLast,
            long totalBannedUsers) {
        return new BannedUsersPageResponse(
                content,
                pageNumber,
                pageSize,
                totalElements,
                totalPages,
                isLast,
                totalBannedUsers);
    }
}
