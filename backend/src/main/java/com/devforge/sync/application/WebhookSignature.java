package com.devforge.sync.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies that a webhook really came from the configured git host.
 *
 * <p>The endpoint is unauthenticated — a git host has no DevForge session — so this
 * signature is the only thing standing between "GitHub pushed" and "anyone who
 * guessed the URL". It is therefore the security boundary of the whole feature.
 *
 * <p>Supports the two conventions in the wild:
 *
 * <ul>
 *   <li>GitHub sends {@code X-Hub-Signature-256: sha256=<hex>}</li>
 *   <li>Gitea and Forgejo send {@code X-Gitea-Signature} / {@code X-Forgejo-Signature}
 *       as bare hex</li>
 * </ul>
 *
 * <p>Both are HMAC-SHA256 over the exact request body, which is why the controller
 * must read the raw bytes rather than a parsed and re-serialised object: re-encoding
 * JSON changes whitespace and key order, and the signature would never match.
 */
public final class WebhookSignature {

    private WebhookSignature() {
    }

    /**
     * Whether {@code presented} is a valid signature of {@code body}.
     *
     * <p>Compared with {@link MessageDigest#isEqual}, which is constant-time. A
     * {@code String.equals} would return as soon as two bytes differed, leaking how
     * much of a guessed signature was correct and making the secret recoverable one
     * byte at a time.
     *
     * @param presented the header value, with or without a {@code sha256=} prefix
     * @return false for a missing, malformed, or wrong signature — never an exception,
     *         because every one of those is the same answer to the caller
     */
    public static boolean matches(byte[] body, String secret, String presented) {
        if (body == null || secret == null || secret.isEmpty()
                || presented == null || presented.isBlank()) {
            return false;
        }

        String hex = presented.strip();
        int equals = hex.indexOf('=');
        if (equals >= 0) {
            // `sha256=abc...`. Anything but sha256 is not something we can verify.
            if (!hex.regionMatches(true, 0, "sha256=", 0, "sha256=".length())) {
                return false;
            }
            hex = hex.substring(equals + 1);
        }

        byte[] offered;
        try {
            offered = HexFormat.of().parseHex(hex.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        return MessageDigest.isEqual(sign(body, secret), offered);
    }

    /** HMAC-SHA256 of the body under the secret. */
    public static byte[] sign(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Hex form, for tests and for showing an operator what to expect. */
    public static String hex(byte[] body, String secret) {
        return HexFormat.of().formatHex(sign(body, secret));
    }
}
