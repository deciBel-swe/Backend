package software.decibel.dtos.user;

import software.decibel.enums.SocialPlatform;

public record SocialLinksDto(SocialPlatform platform,
        String url) {


}
