package com.devforge.identity.application;

import com.devforge.identity.contract.GitCredentials;
import com.devforge.identity.domain.GitAccessToken;
import com.devforge.identity.domain.GitAccessTokenRepository;
import com.devforge.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Git credentials: minting, listing, revoking, and verifying.
 *
 * <p>The verifying half is published as {@link GitCredentials} so the module that
 * serves git can authenticate without being able to mint anything.
 */
@Service
@Transactional
public class GitCredentialService implements GitCredentials {

    private final GitAccessTokenRepository repository;
    private final UserRepository userRepository;

    public GitCredentialService(
            GitAccessTokenRepository repository,
            UserRepository userRepository
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>An unknown token, a revoked one, and an expired one all return empty. A
     * caller that could tell them apart would be a way to confirm that a token
     * existed, which is more than an unauthenticated request should learn.
     */
    @Override
    public Optional<GitIdentity> authenticate(String secret) {
        if (secret == null || secret.isBlank() || !secret.startsWith(GitAccessToken.PREFIX)) {
            return Optional.empty();
        }

        return repository.findByTokenHash(GitAccessToken.hash(secret))
                .filter(token -> !token.hasExpired())
                .flatMap(token -> {
                    token.markUsed();
                    return userRepository.findById(token.getUserId())
                            .map(user -> new GitIdentity(
                                    user.getId(), user.getHandle(),
                                    user.getDisplayName(), user.getEmail()));
                });
    }

    /**
     * Mints a token, returning the secret once.
     *
     * <p>Only the digest is kept, so this response is the only opportunity to read
     * it — which is why the interface says so rather than offering to show it later.
     */
    public GitAccessToken.Issued issue(UUID userId, String name, Instant expiresAt) {
        GitAccessToken.Issued issued = GitAccessToken.issue(userId, name.trim(), expiresAt);
        repository.save(issued.token());
        return issued;
    }

    @Transactional(readOnly = true)
    public List<GitAccessToken> listFor(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** @return false when the token is not this user's, so one cannot revoke another's */
    public boolean revoke(UUID tokenId, UUID userId) {
        return repository.findByIdAndUserId(tokenId, userId)
                .map(token -> {
                    repository.delete(token);
                    return true;
                })
                .orElse(false);
    }
}
