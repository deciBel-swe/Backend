package software.decibel.dtos.auth;

import software.decibel.entities.Token;

public record IssuedToken(String rawToken, Token token) {

}
