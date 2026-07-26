package com.devforge.identity.application;

import java.time.Instant;

public record AuthenticationResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        CurrentUserResponse user
) {

    public static AuthenticationResponse of(IssuedToken token, CurrentUserResponse user) {
        return new AuthenticationResponse(token.token(), "Bearer", token.expiresAt(), user);
    }
}
