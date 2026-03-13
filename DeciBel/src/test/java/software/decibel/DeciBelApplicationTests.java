package software.decibel;

import software.decibel.dtos.PrivacyUpdateRequest;
import software.decibel.dtos.PrivacyUpdateResponse;
import software.decibel.dtos.user.ChangeEmailRequest;
import software.decibel.dtos.user.VerifyEmailChangeRequest;
import software.decibel.entities.PendingEmailChange;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeciBelApplicationTests {

	@Test
	void privacyUpdateRequest_recordStoresValues() {
		PrivacyUpdateRequest request = new PrivacyUpdateRequest(true, false);
		assertTrue(request.isPrivate());
		assertFalse(request.showHistory());
	}

	@Test
	void privacyUpdateResponse_recordStoresValues() {
		PrivacyUpdateResponse response = new PrivacyUpdateResponse(false, true);
		assertFalse(response.isPrivate());
		assertTrue(response.showHistory());
	}

	@Test
	void changeEmailRequest_recordStoresValues() {
		ChangeEmailRequest request = new ChangeEmailRequest("new@example.com");
		assertEquals("new@example.com", request.newEmail());
	}

	@Test
	void verifyEmailChangeRequest_recordStoresValues() {
		VerifyEmailChangeRequest request = new VerifyEmailChangeRequest("token-value");
		assertEquals("token-value", request.token());
	}

	@Test
	void pendingEmailChange_builderStoresValues() {
		User user = User.builder().id(1L).email("old@example.com").username("user").build();
		Token token = Token.builder().tokenId(2L).hash("hashed-token").expiresAt(LocalDateTime.now().plusMinutes(15)).build();
		PendingEmailChange pendingEmailChange = PendingEmailChange.builder()
				.pendingEmailChangeId(3L)
				.user(user)
				.newEmail("new@example.com")
				.token(token)
				.build();

		assertEquals(3L, pendingEmailChange.getPendingEmailChangeId());
		assertEquals(user, pendingEmailChange.getUser());
		assertEquals("new@example.com", pendingEmailChange.getNewEmail());
		assertEquals(token, pendingEmailChange.getToken());
		assertNotNull(pendingEmailChange);
	}
}
