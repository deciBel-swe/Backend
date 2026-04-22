package software.decibel.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import software.decibel.services.auth.AccountRecoveryService;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryControllerTest {

    @Mock
    private AccountRecoveryService accountRecoveryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        software.decibel.controllers.AccountRecoveryController controller = new software.decibel.controllers.AccountRecoveryController(accountRecoveryService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    void forgotPassword_whenRequestIsValid_returnsOkAndBody() throws Exception {
        mockMvc.perform(
                        post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"test@example.com\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("If an account with that email exists, a reset link has been sent."));

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountRecoveryService).forgotPassword(emailCaptor.capture());
        assertEquals("test@example.com", emailCaptor.getValue());
    }

    @Test
    void forgotPassword_whenEmailIsInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"not-an-email\"}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountRecoveryService);
    }

    @Test
    void forgotPassword_whenEmailIsBlank_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"\"}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountRecoveryService);
    }

    @Test
    void forgotPassword_whenEmailIsMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountRecoveryService);
    }
    @Test
    void resetPassword_whenRequestIsValid_returnsOkAndBody() throws Exception {
        mockMvc.perform(
                        post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"raw-reset-token\",\"newPassword\":\"NewPassword1!\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successful."));

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountRecoveryService).resetPassword(tokenCaptor.capture(), passwordCaptor.capture());
        assertEquals("raw-reset-token", tokenCaptor.getValue());
        assertEquals("NewPassword1!", passwordCaptor.getValue());
    }

    @Test
    void resetPassword_whenTokenIsBlank_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"\",\"newPassword\":\"NewPassword1!\"}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountRecoveryService);
    }

    @Test
    void resetPassword_whenPasswordIsWeak_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"raw-reset-token\",\"newPassword\":\"weakpass\"}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountRecoveryService);
    }

    @Test
    void resetPassword_whenPasswordIsMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"raw-reset-token\"}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountRecoveryService);
    }

    @Test
    void resetPassword_whenTokenIsMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"newPassword\":\"NewPassword1!\"}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountRecoveryService);
    }
}
    
