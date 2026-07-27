package com.devforge.identity.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a git-over-HTTP credential to the account that owns it.
 *
 * <p>Published so the module serving git can authenticate a request without seeing
 * how tokens are stored or verified — the same arrangement as
 * {@link AccountProvisioning}. It answers one question, and deliberately cannot mint
 * or list tokens: serving a fetch has no business doing either.
 */
public interface GitCredentials {

    /**
     * @param secret the password git presented over HTTP Basic
     * @return who it belongs to, or empty for an unknown, revoked, or expired token
     *         — all of which are the same answer to the caller
     */
    Optional<GitIdentity> authenticate(String secret);

    /**
     * Who is pushing.
     *
     * <p>More than an id because git needs a name and an address: the servlet
     * container reports a remote user, the reflog records who moved a ref, and a
     * commit DevForge authors on someone's behalf has to say whose it is.
     */
    record GitIdentity(UUID userId, String handle, String displayName, String email) {
    }
}
