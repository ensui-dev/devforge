package com.devforge.identity.contract;

/**
 * Creates and administers accounts on behalf of another module.
 *
 * <p>Published so the instance module can create the first administrator during
 * setup without reaching into identity's persistence, and without duplicating
 * password hashing or handle allocation.
 *
 * <p>Deliberately narrow: it does not authenticate, and it applies no registration
 * policy. Policy is the caller's business — setup creates an admin on an instance
 * that has no policy yet, and an operator creates accounts on a closed one.
 */
public interface AccountProvisioning {

    /**
     * @param instanceAdmin whether this account may configure the instance
     * @throws com.devforge.shared.exception.DuplicateResourceException if the
     *         address is already registered
     */
    UserRef createAccount(String email, String displayName, String password, boolean instanceAdmin);

    /**
     * Grants or revokes instance administration.
     *
     * <p>Refuses to revoke the last administrator: an instance with none can never
     * have its settings changed again, and nothing in the product can undo that.
     *
     * @throws com.devforge.shared.exception.DomainValidationException if this would
     *         leave the instance with no administrator
     */
    UserRef setInstanceAdmin(java.util.UUID userId, boolean instanceAdmin);
}
