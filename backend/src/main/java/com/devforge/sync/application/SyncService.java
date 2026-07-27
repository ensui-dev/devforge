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
    private final HostedRepositories repositories;
    private final SecureRandom random = new SecureRandom();

    public SyncService(
            SyncConfigurationRepository repository,
            WorkspaceAccess workspaceAccess,
            SecretCipher cipher,
            SyncRunner runner,
            AuditTrail auditTrail,
            HostedRepositories repositories
    ) {
        this.repository = repository;
        this.workspaceAccess = workspaceAccess;
        this.cipher = cipher;
        this.runner = runner;
        this.auditTrail = auditTrail;
        this.repositories = repositories;
    }

    public SyncSettingsResponse describe(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);

        return repository.findByWorkspaceId(workspaceId)
                .map(configuration -> SyncSettingsResponse.from(configuration))
                .orElseGet(SyncSettingsResponse::notConfigured);
    }

    /**
     * The repository DevForge hosts for this workspace.
     *
     * <p>Readable by any member, unlike the sync settings: the clone URL is not a
     * secret — the token is — and a member who cannot see the address cannot use
     * the feature at all.
     */
    public GitRepositoryResponse describeRepository(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        if (!repositories.enabled()) {
            return GitRepositoryResponse.disabled();
        }

        return new GitRepositoryResponse(
                true,
                repositories.exists(workspaceId),
                workspaceAccess.addressOf(workspaceId)
                        .map(address -> "/git/" + address + ".git")
                        .orElse(null),
                repositories.sizeOf(workspaceId).orElse(null));
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

        return SyncSettingsResponse.from(saved);
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

        return SyncSettingsResponse.from(configuration);
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

        runner.run(configuration, userId);
        // Read back from the configuration rather than from the outcome: the two
        // must agree, and a webhook-triggered sync only ever populates the former.
        return SyncSettingsResponse.from(configuration);
    }

    /**
     * Runs a sync in response to a verified webhook.
     *
     * <p>Takes the id and loads the configuration again rather than accepting the
     * one {@link #findForWebhook} handed the controller. That instance came from a
     * different transaction and is detached, so everything a run records on it — the
     * status, the message, the commit, the problems — would be written to an object
     * JPA is no longer tracking and silently dropped. The documents were still
     * applied, which is what made it hard to notice: the sync worked, and the
     * settings screen said it had never run.
     *
     * <p>No workspace permission is checked, and none could be: a git host has no
     * DevForge account. The signature is what authorises the call, which is why
     * {@link #findForWebhook} refuses to hand back a configuration without one.
     */
    @Transactional
    public SyncOutcome syncFromWebhook(UUID webhookId, byte[] payload) {
        SyncConfiguration configuration = repository.findByWebhookId(webhookId)
                .filter(SyncConfiguration::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Sync configuration", webhookId));

        Optional<PushEvent> push = PushEvent.parse(payload);

        // A push to another branch is not this workspace's business. Without this
        // check any branch would trigger a sync of the configured one, so pushing a
        // draft would apply main — work nobody asked for, attributed to a push that
        // did not contain it.
        if (push.isPresent() && !push.get().movedBranch(configuration.getBranch())) {
            return SyncOutcome.ignored(
                    "Ignored a push to %s; this workspace follows %s."
                            .formatted(push.get().branchName(), configuration.getBranch()));
        }

        // The commit, not the branch. See PushEvent for why the difference matters.
        return runner.run(configuration, null, push.map(PushEvent::commitId).orElse(null));
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
