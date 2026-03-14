package software.decibel.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.user.ChangeEmailRequest;
import software.decibel.dtos.user.VerifyEmailChangeRequest;
import software.decibel.services.UserEmailService;

@ExtendWith(MockitoExtension.class)
class UserEmailControllerTest {

    @Mock
    private UserEmailService userEmailService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        UserEmailController controller = new UserEmailController(userEmailService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void requestEmailChange_whenRequestIsValid_returnsOkAndBody() throws Exception {
        when(userEmailService.requestMyEmailChange(isNull(), any(ChangeEmailRequest.class)))
                .thenReturn(new MessageResponse("Verification email sent successfully"));

        mockMvc.perform(
                patch("/users/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"new@example.com\"}")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification email sent successfully"));

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        ArgumentCaptor<ChangeEmailRequest> requestCaptor = ArgumentCaptor.forClass(ChangeEmailRequest.class);
        verify(userEmailService).requestMyEmailChange(authCaptor.capture(), requestCaptor.capture());
        assertNull(authCaptor.getValue());
        assertEquals("new@example.com", requestCaptor.getValue().newEmail());
    }

    @Test
    void requestEmailChange_whenBodyIsInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(
                patch("/users/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"\"}")
        )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userEmailService);
    }

    @Test
    void verifyEmailChange_whenAuthenticationExists_passesItToService() throws Exception {
        when(userEmailService.verifyMyEmailChange(any(Authentication.class), any(VerifyEmailChangeRequest.class)))
                .thenReturn(new MessageResponse("Email changed successfully"));

        Authentication authentication = new UsernamePasswordAuthenticationToken("1", "N/A");

        mockMvc.perform(
                post("/users/me/email/verify")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailChangeRequest("verify-token")))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email changed successfully"));

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        ArgumentCaptor<VerifyEmailChangeRequest> requestCaptor = ArgumentCaptor.forClass(VerifyEmailChangeRequest.class);
        verify(userEmailService).verifyMyEmailChange(authCaptor.capture(), requestCaptor.capture());
        assertEquals("1", authCaptor.getValue().getName());
        assertEquals("verify-token", requestCaptor.getValue().token());
    }
}
