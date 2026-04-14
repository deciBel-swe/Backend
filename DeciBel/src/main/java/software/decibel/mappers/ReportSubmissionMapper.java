package software.decibel.mappers;

import org.mapstruct.Mapper;

import software.decibel.dtos.auth.MessageResponse;

@Mapper(componentModel = "spring")
public interface ReportSubmissionMapper {

    default MessageResponse toTrackReportSubmittedResponse() {
        return new MessageResponse("Track reported successfully");
    }

    default MessageResponse toCommentReportSubmittedResponse() {
        return new MessageResponse("Comment reported successfully");
    }
}
