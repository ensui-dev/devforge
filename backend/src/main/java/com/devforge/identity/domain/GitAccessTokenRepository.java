package com.devforge.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitAccessTokenRepository extends JpaRepository<GitAccessToken, UUID> {

    /** One indexed lookup, which is why the digest is unsalted. */
    Optional<GitAccessToken> findByTokenHash(String tokenHash);

    List<GitAccessToken> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<GitAccessToken> findByIdAndUserId(UUID id, UUID userId);
}
