package software.decibel.mappers;

import org.mapstruct.Mapper;
import software.decibel.dtos.track.responses.RepostResponse;

@Mapper(componentModel = "spring")
public interface RepostMapper {

    default RepostResponse toRepostResponse(boolean isReposted) {
        return new RepostResponse(
                isReposted ? "Track reposted" : "Repost removed",
                isReposted);
    }
}
