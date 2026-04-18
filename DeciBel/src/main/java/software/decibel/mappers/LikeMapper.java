package software.decibel.mappers;

import org.mapstruct.Mapper;
import software.decibel.dtos.track.responses.LikeResponse;

@Mapper(componentModel = "spring")
public interface LikeMapper {

    default LikeResponse toLikeResponse(boolean isLiked) {
        return new LikeResponse(
                isLiked ? " liked" : "Like removed",
                isLiked);
    }
}
