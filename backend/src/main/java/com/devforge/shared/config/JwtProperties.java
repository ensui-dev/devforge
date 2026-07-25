package com.devforge.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Signing configuration for access tokens.
 *
 * @param secret HMAC-SHA256 signing key. Validated at startup so a short or
 *               missing key fails the boot rather than silently weakening tokens.
 * @param issuer value placed in, and required of, the {@code iss} claim
 * @param ttl    how long an issued token stays valid
 */
@Validated
@ConfigurationProperties(prefix = "devforge.jwt")
public record JwtProperties(
        @NotBlank
        @Size(min = 32, message = "must be at least 32 characters to key HMAC-SHA256")
        String secret,

        @NotBlank String issuer,

        Duration ttl
) {

    public JwtProperties {
        ttl = ttl == null ? Duration.ofHours(12) : ttl;
    }
}
