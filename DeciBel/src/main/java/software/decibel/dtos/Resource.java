package software.decibel.dtos;

import software.decibel.enums.ResourceType;

public record Resource(
        ResourceType type,
        Long id) {

}
