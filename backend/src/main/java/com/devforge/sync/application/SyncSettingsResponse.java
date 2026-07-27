package com.devforge.sync.application;

import com.devforge.document.contract.DocumentType;
import com.devforge.sync.domain.DeletionPolicy;
import com.devforge.sync.domain.SyncConfiguration;
import com.devforge.sync.domain.SyncStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The configuration as the settings screen sees it.
 *
 * <p>Credentials are never returned — only whether one is stored. A response that
 * echoed a repository token would put it in every browser cache, proxy log, and
 * developer console that touched the page, which defeats encrypting it at rest.
 *
 * @param webhookUrl     the path a git host should call. Not a secret on its own; the
 *                       signature is what authenticates a delivery.
 * @param hasAccessToken whether a token is stored, so the form can say "replace" and
 *                       distinguish that from "add"
 */
public record SyncSettingsResponse(
        boolean configured,
        String repositoryUrl,
        String branch,
        String documentPath,
        DocumentType defaultType,
        DeletionPolicy deletionPolicy,
        boolean enabled,
        boolean hasAccessToken,
        boolean hasWebhookSecret,
        String webhookUrl,
        UUID webhookId,
        Instant lastAttemptedAt,
        Instant lastSucceededAt,
        String lastRef,
        SyncStatus lastStatus,
        String lastMessage,
        int lastCreated,
        int lastUpdated,
        int lastArchived,
        int lastUnchanged,
        /** Problems from the most recent run, when it was only partly applied. */
        List<String> problems
) {

    public static SyncSettingsResponse from(SyncConfiguration configuration) {
        return new SyncSettingsResponse(
                configuration.isConfigured(),
                configuration.getRepositoryUrl(),
                configuration.getBranch(),
                configuration.getDocumentPath(),
                configuration.getDefaultType(),
                configuration.getDeletionPolicy(),
                configuration.isEnabled(),
                configuration.getAccessToken() != null,
                configuration.getWebhookSecret() != null,
                "/api/public/sync/" + configuration.getWebhookId(),
                configuration.getWebhookId(),
                configuration.getLastAttemptedAt(),
                configuration.getLastSucceededAt(),
                configuration.getLastRef(),
                configuration.getLastStatus(),
                configuration.getLastMessage(),
                configuration.getLastCreated(),
                configuration.getLastUpdated(),
                configuration.getLastArchived(),
                configuration.getLastUnchanged(),
                configuration.getLastProblems());
    }

    /** Before anything has been set up. */
    public static SyncSettingsResponse notConfigured() {
        return new SyncSettingsResponse(
                false, "", "main", "", DocumentType.GENERAL, DeletionPolicy.ARCHIVE,
                false, false, false, null, null, null, null, null, null, null,
                0, 0, 0, 0, List.of());
    }
}
