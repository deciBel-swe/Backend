package software.decibel.mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import software.decibel.dtos.auth.MessageResponse;

class ReportSubmissionMapperTest {

    private final ReportSubmissionMapper mapper = Mappers.getMapper(ReportSubmissionMapper.class);

    @Test
    void toTrackReportSubmittedResponse_returnsExpectedMessage() {
        MessageResponse response = mapper.toTrackReportSubmittedResponse();

        assertEquals("Track reported successfully", response.message());
    }

    @Test
    void toCommentReportSubmittedResponse_returnsExpectedMessage() {
        MessageResponse response = mapper.toCommentReportSubmittedResponse();

        assertEquals("Comment reported successfully", response.message());
    }
}
