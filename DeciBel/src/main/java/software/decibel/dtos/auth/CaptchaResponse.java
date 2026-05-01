package software.decibel.dtos.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptchaResponse(
        @JsonProperty("tokenProperties")
        TokenProperties tokenProperties,
        @JsonProperty("riskAnalysis")
        RiskAnalysis riskAnalysis) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenProperties(
            @JsonProperty("valid")
            boolean valid,
            @JsonProperty("invalidReason")
            String invalidReason,
            @JsonProperty("action")
            String action) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskAnalysis(@JsonProperty("score")
            Double score) {

    }

}
