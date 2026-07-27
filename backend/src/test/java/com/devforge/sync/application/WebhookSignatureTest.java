package com.devforge.sync.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Webhook signature verification.
 *
 * <p>This is the security boundary of the sync feature: the endpoint is
 * unauthenticated, so the signature is all that separates "the git host pushed" from
 * "someone found the URL". Most of these tests are about what it refuses.
 */
class WebhookSignatureTest {

    private static final String SECRET = "a-shared-webhook-secret";
    private static final byte[] BODY = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsAGitHubStyleSignature() {
        String header = "sha256=" + WebhookSignature.hex(BODY, SECRET);

        assertThat(WebhookSignature.matches(BODY, SECRET, header)).isTrue();
    }

    /** Gitea and Forgejo send bare hex with no algorithm prefix. */
    @Test
    void acceptsABareHexSignature() {
        assertThat(WebhookSignature.matches(BODY, SECRET, WebhookSignature.hex(BODY, SECRET)))
                .isTrue();
    }

    @Test
    void acceptsUppercaseHexAndSurroundingWhitespace() {
        String hex = WebhookSignature.hex(BODY, SECRET).toUpperCase();

        assertThat(WebhookSignature.matches(BODY, SECRET, "  " + hex + "  ")).isTrue();
        assertThat(WebhookSignature.matches(BODY, SECRET, "SHA256=" + hex)).isTrue();
    }

    @Test
    void rejectsTheWrongSecret() {
        String header = WebhookSignature.hex(BODY, "a-different-secret");

        assertThat(WebhookSignature.matches(BODY, SECRET, header)).isFalse();
    }

    /** The whole point: a body that changed in transit must not verify. */
    @Test
    void rejectsATamperedBody() {
        String header = WebhookSignature.hex(BODY, SECRET);
        byte[] tampered = "{\"ref\":\"refs/heads/evil\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(WebhookSignature.matches(tampered, SECRET, header)).isFalse();
    }

    @Test
    void rejectsAMissingSignature() {
        assertThat(WebhookSignature.matches(BODY, SECRET, null)).isFalse();
        assertThat(WebhookSignature.matches(BODY, SECRET, "")).isFalse();
        assertThat(WebhookSignature.matches(BODY, SECRET, "   ")).isFalse();
    }

    /** No secret configured means nothing can be verified, so nothing is accepted. */
    @Test
    void rejectsEverythingWhenNoSecretIsSet() {
        assertThat(WebhookSignature.matches(BODY, null, "anything")).isFalse();
        assertThat(WebhookSignature.matches(BODY, "", "anything")).isFalse();
    }

    @Test
    void rejectsMalformedHexWithoutThrowing() {
        assertThat(WebhookSignature.matches(BODY, SECRET, "not-hex-at-all")).isFalse();
        assertThat(WebhookSignature.matches(BODY, SECRET, "sha256=zzzz")).isFalse();
        // Odd length cannot be bytes.
        assertThat(WebhookSignature.matches(BODY, SECRET, "abc")).isFalse();
    }

    /** An algorithm we cannot verify must be refused, not assumed to be SHA-256. */
    @Test
    void rejectsAnAlgorithmItDoesNotImplement() {
        String hex = WebhookSignature.hex(BODY, SECRET);

        assertThat(WebhookSignature.matches(BODY, SECRET, "sha1=" + hex)).isFalse();
        assertThat(WebhookSignature.matches(BODY, SECRET, "md5=" + hex)).isFalse();
    }

    /** A truncated signature must not pass on the strength of its correct prefix. */
    @Test
    void rejectsATruncatedSignature() {
        String hex = WebhookSignature.hex(BODY, SECRET);

        assertThat(WebhookSignature.matches(BODY, SECRET, hex.substring(0, 32))).isFalse();
        assertThat(WebhookSignature.matches(BODY, SECRET, hex + "00")).isFalse();
    }

    @Test
    void verifiesAnEmptyBody() {
        byte[] empty = new byte[0];

        assertThat(WebhookSignature.matches(empty, SECRET, WebhookSignature.hex(empty, SECRET)))
                .isTrue();
        assertThat(WebhookSignature.matches(empty, SECRET, WebhookSignature.hex(BODY, SECRET)))
                .isFalse();
    }

    /**
     * The signature must cover the exact bytes. Re-serialising JSON changes
     * whitespace and key order, which is why the controller reads the raw body.
     */
    @Test
    void isSensitiveToInsignificantJsonWhitespace() {
        byte[] reformatted = "{ \"ref\": \"refs/heads/main\" }".getBytes(StandardCharsets.UTF_8);

        assertThat(WebhookSignature.matches(reformatted, SECRET, WebhookSignature.hex(BODY, SECRET)))
                .isFalse();
    }

    @Test
    void signsConsistently() {
        assertThat(WebhookSignature.hex(BODY, SECRET))
                .isEqualTo(WebhookSignature.hex(BODY, SECRET))
                .hasSize(64);
    }
}
