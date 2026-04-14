package software.decibel.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.moderation.ReportRequest;
import software.decibel.exceptions.GlobalExceptionHandler;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.services.ReportService;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ReportController controller = new ReportController(reportService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
    }

    @Test
    void reportTrack_whenRequestIsValid_returnsSuccessMessage() throws Exception {
        when(reportService.reportTrack(eq(15L), any(ReportRequest.class)))
                .thenReturn(new MessageResponse("Track reported successfully"));

        mockMvc.perform(post("/tracks/15/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReportRequest("Spam", "Misleading metadata"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Track reported successfully"));
    }

    @Test
    void reportTrack_whenReasonIsMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tracks/15/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Misleading metadata\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    void reportTrack_whenReasonIsBlank_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tracks/15/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"   \",\"description\":\"Misleading metadata\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    void reportTrack_whenServiceThrowsUnauthorized_returnsUnauthorized() throws Exception {
        when(reportService.reportTrack(eq(15L), any(ReportRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication token is missing"));

        mockMvc.perform(post("/tracks/15/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReportRequest("Spam", null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication token is missing"));
    }

    @Test
    void reportTrack_whenTrackDoesNotExist_returnsNotFound() throws Exception {
        when(reportService.reportTrack(eq(15L), any(ReportRequest.class)))
                .thenThrow(new ResourceNotFoundException("Track with id 15 not found"));

        mockMvc.perform(post("/tracks/15/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReportRequest("Spam", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Track with id 15 not found"));
    }

    @Test
    void reportComment_whenRequestIsValid_returnsSuccessMessage() throws Exception {
        when(reportService.reportComment(eq(21L), any(ReportRequest.class)))
                .thenReturn(new MessageResponse("Comment reported successfully"));

        mockMvc.perform(post("/comments/21/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReportRequest("Harassment", "Offensive reply"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Comment reported successfully"));
    }

    @Test
    void reportComment_whenCommentDoesNotExist_returnsNotFound() throws Exception {
        when(reportService.reportComment(eq(21L), any(ReportRequest.class)))
                .thenThrow(new ResourceNotFoundException("Comment with id 21 not found"));

        mockMvc.perform(post("/comments/21/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReportRequest("Harassment", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comment with id 21 not found"));
    }
}
