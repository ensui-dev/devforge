package com.devforge.identity.domain;

import com.devforge.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A credential for pushing to, and cloning from, DevForge over HTTPS.
 *
 * <p>Only the digest is stored. Like a password, a token never needs reading back —
 * only verifying — so there is nothing here to leak. Unlike a password it is 256
 * random bits, which is why the digest is a plain SHA-256 rather than bcrypt: a work
 * factor defends low-entropy secrets against guessing, and would only cost time on
 * every git request, of which a clone makes several.
 */
@Entity
@Table(name = "git_access_tokens")
public class GitAccessToken extends BaseEntity {

    /** Marks the string as one of ours, so a leak is recognisable in a log or a scan. */
    public static final String PREFIX = "dfg_";

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false, unique = true)
    private String tokenHash;

    @Column(name = "token_hint", nullable = false, length = 16, updatable = false)
    private String tokenHint;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected GitAccessToken() {
        // for JPA
    }

    private GitAccessToken(UUID userId, String name, String secret, Instant expiresAt) {
        this.userId = userId;
        this.name = name;
        this.tokenHash = hash(secret);
        // Enough to tell two tokens apart, far too little to reconstruct one.
        this.tokenHint = secret.substring(0, Math.min(secret.length(), 12));
        this.expiresAt = expiresAt;
    }

    /**
     * Mints a token.
     *
     * @return the entity and the secret, which is the only time the secret exists
     *         in readable form
     */
    public static Issued issue(UUID userId, String name, Instant expiresAt) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(new GitAccessToken(userId, name, secret, expiresAt), secret);
    }

    public record Issued(GitAccessToken token, String secret) {
    }

    public static String hash(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public boolean hasExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * Records that the token was used.
     *
     * <p>Rounded to the minute so a clone, which authenticates several times in a
     * second, does not write a row per request.
     */
    public void markUsed() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        if (lastUsedAt == null || lastUsedAt.isBefore(now)) {
            this.lastUsedAt = now;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getTokenHint() {
        return tokenHint;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
