package com.devforge.identity.application;

import java.time.Instant;

public record AuthenticationResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user
) {

    public static AuthenticationResponse of(IssuedToken token, UserResponse user) {
        return new AuthenticationResponse(token.token(), "Bearer", token.expiresAt(), user);
    }
}
