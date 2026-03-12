package software.decibel;

import software.decibel.dtos.PrivacyUpdateRequest;
import software.decibel.dtos.PrivacyUpdateResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
