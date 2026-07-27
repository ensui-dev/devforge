package com.devforge.shared.security;

import com.devforge.shared.config.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Encrypts secrets that have to be stored recoverably.
 *
 * <p>Passwords are hashed, never encrypted — nothing needs to read them back. But
 * some credentials must be usable later: a repository access token is presented to
 * a git host on every sync, and a webhook secret has to be re-derived to verify a
 * signature. Those cannot be one-way.
 *
 * <p>Storing them in plaintext would mean a leaked database dump or backup hands
 * over live third-party credentials. AES-GCM with a key derived from the
 * instance's signing secret raises that from "read the column" to "also obtain the
 * environment", which is the realistic difference between a stolen backup and a
 * compromised host.
 *
 * <p>This is defence in depth, not a vault. An attacker with the running host has
 * the key. It is worth having anyway, because database dumps travel and hosts
 * mostly do not.
 *
 * <p>Rotating {@code DEVFORGE_JWT_SECRET} makes existing ciphertexts
 * undecryptable. {@link #decrypt} reports that as an empty result rather than
 * throwing, so the caller can tell the operator to reconnect the repository
 * instead of failing to start.
 */
@Component
public class SecretCipher {

    /** Prefixed so a stored value's scheme is identifiable if this ever changes. */
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(JwtProperties properties) {
        this.key = deriveKey(properties.secret());
    }

    /**
     * @return {@code null} for null or blank input, so callers can pass optional
     *         fields straight through
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt secret", e);
        }
    }

    /**
     * @return empty when the value is absent, or when it cannot be decrypted because
     *         the signing secret has changed since it was written
     */
    public Optional<String> decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return Optional.empty();
        }
        if (!stored.startsWith(PREFIX)) {
            // Written by an older build, or tampered with. Either way it is not a
            // ciphertext this class produced.
            return Optional.empty();
        }

        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (combined.length <= IV_BYTES) {
                return Optional.empty();
            }

            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = java.util.Arrays.copyOfRange(combined, IV_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return Optional.of(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8));
        } catch (AEADBadTagException e) {
            // The usual cause: the signing secret was rotated. Not an error worth
            // crashing over — the operator needs to re-enter the credential.
            return Optional.empty();
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * AES-256 key from the instance signing secret.
     *
     * <p>SHA-256 rather than a password-based KDF on purpose: the input is already a
     * high-entropy random secret of at least 32 characters, which the configuration
     * validates at startup. Iterating a KDF over it would cost time without adding
     * resistance to anything.
     */
    private static SecretKeySpec deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Domain-separated, so this key is not the token signing key even though
            // both derive from the same secret.
            digest.update("devforge:secret-cipher:v1".getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
