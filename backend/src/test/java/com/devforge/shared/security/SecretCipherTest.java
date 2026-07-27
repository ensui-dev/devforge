package com.devforge.shared.security;

import com.devforge.shared.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Encryption of credentials that have to be stored recoverably.
 *
 * <p>The properties that matter are round-tripping, that ciphertexts are not
 * deterministic, and — most of all — that a rotated signing secret degrades to an
 * empty result rather than an exception. The last one decides whether rotating the
 * secret is a routine operation or an outage.
 */
class SecretCipherTest {

    private static final String SECRET = "a-signing-secret-of-at-least-32-characters";

    private SecretCipher cipherWith(String secret) {
        return new SecretCipher(new JwtProperties(secret, "devforge", Duration.ofHours(12)));
    }

    private final SecretCipher cipher = cipherWith(SECRET);

    @Test
    void roundTripsAValue() {
        String token = "ghp_aRealisticLookingAccessToken1234567890";

        assertThat(cipher.decrypt(cipher.encrypt(token))).contains(token);
    }

    @Test
    void roundTripsUnicodeAndPunctuation() {
        String awkward = "sécret/with+slashes=and:colons — and an emoji 🔧";

        assertThat(cipher.decrypt(cipher.encrypt(awkward))).contains(awkward);
    }

    /** A fresh IV each time, so identical secrets do not produce identical columns. */
    @Test
    void encryptsTheSameValueDifferentlyEachTime() {
        String first = cipher.encrypt("same");
        String second = cipher.encrypt("same");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).contains("same");
        assertThat(cipher.decrypt(second)).contains("same");
    }

    @Test
    void passesNullAndEmptyStraightThrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.encrypt("")).isNull();
        assertThat(cipher.decrypt(null)).isEmpty();
        assertThat(cipher.decrypt("")).isEmpty();
    }

    /**
     * The property that decides whether rotating the signing secret is routine.
     *
     * <p>An exception here would fail whatever request touched the row; an empty
     * result lets the caller say "reconnect the repository".
     */
    @Test
    void reportsAnUndecryptableValueRatherThanThrowing() {
        String underOldSecret = cipher.encrypt("token");
        SecretCipher afterRotation = cipherWith("a-completely-different-secret-32-chars-long");

        assertThat(afterRotation.decrypt(underOldSecret)).isEmpty();
    }

    /** GCM authenticates, so a tampered ciphertext is rejected rather than mangled. */
    @Test
    void rejectsATamperedCiphertext() {
        String encrypted = cipher.encrypt("token");
        // Flip a character in the body, leaving the scheme prefix intact.
        char[] chars = encrypted.toCharArray();
        int index = encrypted.length() - 5;
        chars[index] = chars[index] == 'A' ? 'B' : 'A';

        assertThat(cipher.decrypt(new String(chars))).isEmpty();
    }

    @Test
    void ignoresAValueThatIsNotOneOfItsCiphertexts() {
        assertThat(cipher.decrypt("plaintext-token")).isEmpty();
        assertThat(cipher.decrypt("v2:something-from-the-future")).isEmpty();
        assertThat(cipher.decrypt("v1:not-valid-base64!!!")).isEmpty();
        assertThat(cipher.decrypt("v1:")).isEmpty();
    }

    /** Stored values must be identifiable, so a future scheme can be told apart. */
    @Test
    void marksCiphertextsWithTheirScheme() {
        assertThat(cipher.encrypt("token")).startsWith("v1:");
    }

    /** The stored form must not contain the plaintext, which is the entire point. */
    @Test
    void doesNotLeaveThePlaintextVisible() {
        String encrypted = cipher.encrypt("ghp_secretToken");

        assertThat(encrypted).doesNotContain("ghp_secretToken");
        assertThat(encrypted).doesNotContain("secretToken");
    }

    /** Deriving the same key from the same secret, across restarts. */
    @Test
    void decryptsValuesWrittenByAnotherInstanceWithTheSameSecret() {
        String encrypted = cipher.encrypt("token");
        Optional<String> readByFreshInstance = cipherWith(SECRET).decrypt(encrypted);

        assertThat(readByFreshInstance).contains("token");
    }
}
