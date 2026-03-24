package software.decibel.dtos.user;

import org.hibernate.validator.constraints.URL;

public record SocialLinksDto(
        @URL(message = "Instagram must be a valid URL")
        String instagram,
        @URL(message = "Twitter must be a valid URL")
        String twitter,
        @URL(message = "Website must be a valid URL")
        String website) {

}
