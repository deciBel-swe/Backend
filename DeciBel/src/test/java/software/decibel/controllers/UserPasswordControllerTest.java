package software.decibel.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.decibel.controllers.User.UserPasswordController;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.user.ChangePasswordRequest;
import software.decibel.services.user.UserPasswordService;

@ExtendWith(MockitoExtension.class)
class UserPasswordControllerTest {

    @Mock
    private UserPasswordService userPasswordService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        UserPasswordController controller = new UserPasswordController(userPasswordService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void resetPassword_whenRequestIsValid_returnsOkAndBody() throws Exception {
        when(userPasswordService.resetMyPassword(isNull(), any(ChangePasswordRequest.class)))
                .thenReturn(new MessageResponse("Password changed successfully"));

        Authentication authentication = new UsernamePasswordAuthenticationToken("1", "N/A");

        mockMvc.perform(
                patch("/users/me/reset-password")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("currentPass123!", "NewPass123!")))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        ArgumentCaptor<ChangePasswordRequest> requestCaptor = ArgumentCaptor.forClass(ChangePasswordRequest.class);
        verify(userPasswordService).resetMyPassword(authCaptor.capture(), requestCaptor.capture());
        assertEquals("1", authCaptor.getValue().getName());
        assertEquals("currentPass123!", requestCaptor.getValue().currentPassword());
        assertEquals("NewPass123!", requestCaptor.getValue().newPassword());
    }

    @Test
    void resetPassword_whenBodyIsInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(
                patch("/users/me/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"short\"}")
        )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userPasswordService);
    }
}
