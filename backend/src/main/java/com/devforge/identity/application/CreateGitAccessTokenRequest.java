package com.devforge.identity.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param name       what this token is for, so its owner can tell one machine from
 *                   another when deciding which to revoke
 * @param expiresInDays optional; a token with no expiry works until it is revoked
 */
public record CreateGitAccessTokenRequest(
        @NotBlank @Size(max = 120) String name,
        Integer expiresInDays
) {
}
