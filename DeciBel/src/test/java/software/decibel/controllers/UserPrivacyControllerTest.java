package software.decibel.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import software.decibel.controllers.User.UserPrivacyController;
import software.decibel.dtos.auth.PrivacyUpdateRequest;
import software.decibel.dtos.auth.PrivacyUpdateResponse;
import software.decibel.services.user.UserPrivacyService;

@ExtendWith(MockitoExtension.class)
class UserPrivacyControllerTest {

    @Mock
    private UserPrivacyService userPrivacyService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        UserPrivacyController controller = new UserPrivacyController(userPrivacyService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void updatePrivacy_whenRequestIsValid_returnsOkAndBody() throws Exception {
        when(userPrivacyService.updateMyPrivacy(isNull(), any(PrivacyUpdateRequest.class)))
                .thenReturn(new PrivacyUpdateResponse(true, false));

        mockMvc.perform(
                patch("/users/me/privacy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPrivate\":true,\"showHistory\":false}")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(true))
                .andExpect(jsonPath("$.showHistory").value(false));

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        ArgumentCaptor<PrivacyUpdateRequest> requestCaptor = ArgumentCaptor.forClass(PrivacyUpdateRequest.class);
        verify(userPrivacyService).updateMyPrivacy(authCaptor.capture(), requestCaptor.capture());
        assertNull(authCaptor.getValue());
        assertEquals(true, requestCaptor.getValue().isPrivate());
        assertEquals(false, requestCaptor.getValue().showHistory());
    }

    @Test
    void updatePrivacy_whenMissingRequiredField_returnsBadRequest() throws Exception {
        mockMvc.perform(
                patch("/users/me/privacy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPrivate\":true}")
        )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userPrivacyService);
    }

    @Test
    void updatePrivacy_whenAuthenticationExists_passesItToService() throws Exception {
        when(userPrivacyService.updateMyPrivacy(any(Authentication.class), any(PrivacyUpdateRequest.class)))
                .thenReturn(new PrivacyUpdateResponse(false, true));

        Authentication authentication = new UsernamePasswordAuthenticationToken("1", "N/A");

        mockMvc.perform(
                patch("/users/me/privacy")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PrivacyUpdateRequest(false, true)))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(false))
                .andExpect(jsonPath("$.showHistory").value(true));

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        ArgumentCaptor<PrivacyUpdateRequest> requestCaptor = ArgumentCaptor.forClass(PrivacyUpdateRequest.class);
        verify(userPrivacyService).updateMyPrivacy(authCaptor.capture(), requestCaptor.capture());
        assertEquals("1", authCaptor.getValue().getName());
        assertEquals(false, requestCaptor.getValue().isPrivate());
        assertEquals(true, requestCaptor.getValue().showHistory());
    }
}
