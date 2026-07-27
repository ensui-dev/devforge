package com.devforge.sync.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SyncConfigurationRepository extends JpaRepository<SyncConfiguration, UUID> {

    Optional<SyncConfiguration> findByWorkspaceId(UUID workspaceId);

    /** The webhook knows only its own id; it must not need to know the workspace. */
    Optional<SyncConfiguration> findByWebhookId(UUID webhookId);

    boolean existsByWorkspaceId(UUID workspaceId);
}
