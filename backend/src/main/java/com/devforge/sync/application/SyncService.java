package com.devforge.sync.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.shared.security.SecretCipher;
import com.devforge.sync.domain.SyncConfiguration;
import com.devforge.sync.domain.SyncConfigurationRepository;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Managing a workspace's git connection.
 *
 * <p>Configuration needs {@code ADMIN}: it decides where a workspace's documentation
 * comes from, and pointing it at the wrong repository could withdraw every page.
 * Reading the settings needs {@code ADMIN} too, unlike most of the product — the
 * response says whether credentials are stored and exposes the webhook URL, which is
 * not a member's business.
 */
@Service
@Transactional(readOnly = true)
public class SyncService {

    private final SyncConfigurationRepository repository;
    private final WorkspaceAccess workspaceAccess;
    private final SecretCipher cipher;
    private final SyncRunner runner;
    private final AuditTrail auditTrail;
    private final SecureRandom random = new SecureRandom();

    public SyncService(
            SyncConfigurationRepository repository,
            WorkspaceAccess workspaceAccess,
            SecretCipher cipher,
            SyncRunner runner,
            AuditTrail auditTrail
    ) {
        this.repository = repository;
        this.workspaceAccess = workspaceAccess;
        this.cipher = cipher;
        this.runner = runner;
        this.auditTrail = auditTrail;
    }

    public SyncSettingsResponse describe(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);

        return repository.findByWorkspaceId(workspaceId)
                .map(configuration -> SyncSettingsResponse.from(configuration, List.of()))
                .orElseGet(SyncSettingsResponse::notConfigured);
    }

    @Transactional
    public SyncSettingsResponse configure(
            UUID workspaceId,
            SyncSettingsRequest request,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);

        SyncConfiguration configuration = repository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> new SyncConfiguration(workspaceId));
        boolean isNew = configuration.getLastAttemptedAt() == null
                && !configuration.isConfigured();

        configuration.configure(
                request.repositoryUrl(),
                request.branch(),
                request.documentPath(),
                request.defaultType(),
                request.deletionPolicy(),
                Boolean.TRUE.equals(request.enabled()));

        // Absent means "leave it"; empty means "clear it". A form cannot echo a
        // secret back, so it cannot distinguish those two any other way.
        if (request.accessToken() != null) {
            configuration.setAccessToken(
                    request.accessToken().isBlank() ? null : cipher.encrypt(request.accessToken()));
        }
        if (request.webhookSecret() != null) {
            configuration.setWebhookSecret(
                    request.webhookSecret().isBlank() ? null : cipher.encrypt(request.webhookSecret()));
        }

        SyncConfiguration saved = repository.save(configuration);

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.SYNC_CONFIGURED, AuditTargetType.WORKSPACE)
                .target(workspaceId, saved.getRepositoryUrl())
                .inWorkspace(workspaceId)
                .with("branch", saved.getBranch())
                .with("documentPath", saved.getDocumentPath())
                .with("deletionPolicy", saved.getDeletionPolicy())
                .with("enabled", saved.isEnabled())
                .with("firstTime", isNew));

        return SyncSettingsResponse.from(saved, List.of());
    }

    /**
     * Generates a webhook secret and returns it once.
     *
     * <p>Returned in the clear exactly here and nowhere else: the operator has to
     * paste it into their git host, and it is stored encrypted, so this response is
     * the only opportunity to read it. Generating a new one invalidates the old.
     */
    @Transactional
    public String regenerateWebhookSecret(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);
        SyncConfiguration configuration = require(workspaceId);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        configuration.setWebhookSecret(cipher.encrypt(secret));
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.SYNC_CONFIGURED, AuditTargetType.WORKSPACE)
                .target(workspaceId, configuration.getRepositoryUrl())
                .inWorkspace(workspaceId)
                .with("webhookSecretRotated", true));

        return secret;
    }

    /** Mints a new webhook id, invalidating the published URL. */
    @Transactional
    public SyncSettingsResponse rotateWebhookUrl(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);
        SyncConfiguration configuration = require(workspaceId);

        configuration.rotateWebhookId();
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.SYNC_CONFIGURED, AuditTargetType.WORKSPACE)
                .target(workspaceId, configuration.getRepositoryUrl())
                .inWorkspace(workspaceId)
                .with("webhookUrlRotated", true));

        return SyncSettingsResponse.from(configuration, List.of());
    }

    @Transactional
    public void disconnect(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);
        SyncConfiguration configuration = require(workspaceId);

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.SYNC_CONFIGURED, AuditTargetType.WORKSPACE)
                .target(workspaceId, configuration.getRepositoryUrl())
                .inWorkspace(workspaceId)
                .with("disconnected", true));

        // Removes the stored credentials with it. Documents already synced stay.
        repository.delete(configuration);
    }

    /** Runs a sync on request, so an operator does not have to push to test it. */
    @Transactional
    public SyncSettingsResponse syncNow(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);
        SyncConfiguration configuration = require(workspaceId);

        SyncOutcome outcome = runner.run(configuration, userId);
        return SyncSettingsResponse.from(configuration, outcome.problems());
    }

    /**
     * Runs a sync in response to a verified webhook.
     *
     * <p>No workspace permission is checked, and none could be: a git host has no
     * DevForge account. The signature is what authorises the call, which is why
     * {@link #findForWebhook} refuses to hand back a configuration without one.
     */
    @Transactional
    public SyncOutcome syncFromWebhook(SyncConfiguration configuration) {
        return runner.run(configuration, null);
    }

    /**
     * The configuration a webhook delivery belongs to, and its decrypted secret.
     *
     * <p>Returns empty when no configuration has that id, when syncing is switched
     * off, or when no webhook secret is stored — an unsigned delivery can never be
     * verified, so an endpoint that accepted one would be an unauthenticated way to
     * trigger work.
     */
    public Optional<VerifiableWebhook> findForWebhook(UUID webhookId) {
        return repository.findByWebhookId(webhookId)
                .filter(SyncConfiguration::isEnabled)
                .flatMap(configuration -> cipher.decrypt(configuration.getWebhookSecret())
                        .map(secret -> new VerifiableWebhook(configuration, secret)));
    }

    public record VerifiableWebhook(SyncConfiguration configuration, String secret) {
    }

    private SyncConfiguration require(UUID workspaceId) {
        return repository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Sync configuration", workspaceId));
    }

}
