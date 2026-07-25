package com.devforge.identity.application;

import com.devforge.identity.contract.UserRef;

/**
 * Port for minting access tokens.
 *
 * <p>The application layer depends on this abstraction rather than on Spring
 * Security's encoder, which keeps {@link AuthService} unit testable and confines
 * the choice of token format to the infrastructure package.
 */
public interface AccessTokenIssuer {

    IssuedToken issue(UserRef user);
}
