package software.decibel.dtos.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CaptchaRequest(@JsonProperty("event")
        Event event) {

    public record Event(
            @JsonProperty("token")
            String token,
            @JsonProperty("siteKey")
            String siteKey,
            @JsonProperty("expectedAction")
            String expectedAction) {

    }
}
