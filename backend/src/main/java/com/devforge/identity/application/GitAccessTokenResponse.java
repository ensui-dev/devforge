package com.devforge.identity.application;

import com.devforge.identity.domain.GitAccessToken;

import java.time.Instant;
import java.util.UUID;

/**
 * A git access token as its owner sees it afterwards.
 *
 * <p>The secret is absent, and cannot be recovered: only its digest was stored.
 * {@code hint} is the leading characters, which is enough to recognise which token
 * a machine is using and far too little to reconstruct one.
 */
public record GitAccessTokenResponse(
        UUID id,
        String name,
        String hint,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean expired
) {

    public static GitAccessTokenResponse from(GitAccessToken token) {
        return new GitAccessTokenResponse(
                token.getId(),
                token.getName(),
                token.getTokenHint(),
                token.getCreatedAt(),
                token.getLastUsedAt(),
                token.getExpiresAt(),
                token.hasExpired());
    }
}
