package com.devforge.sync.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DocumentAuthoring;
import com.devforge.document.contract.DocumentAuthoring.ReferenceOutcome;
import com.devforge.document.contract.DocumentDraft;
import com.devforge.shared.security.SecretCipher;
import com.devforge.sync.domain.DeletionPolicy;
import com.devforge.sync.domain.SyncConfiguration;
import com.devforge.sync.domain.SyncStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Applies a repository's documentation to a workspace.
 *
 * <p>Writes documents through {@link DocumentAuthoring}, a contract published by the
 * document module, so nothing here touches a document entity or reimplements what a
 * write guarantees. That is what keeps the two modules independent: sync knows about
 * slugs and drafts, and nothing about revisions or content hashing.
 *
 * <p>Conflicts resolve in git's favour, and lose nothing. A page edited in the
 * interface and then overwritten by a push keeps the hand edit as a revision — the
 * document store is append-only — so the log shows exactly what happened and the
 * earlier version can be restored.
 */
@Service
public class SyncRunner {

    private static final Logger log = LoggerFactory.getLogger(SyncRunner.class);

    private final DocumentSource source;
    private final DocumentAuthoring authoring;
    private final SecretCipher cipher;
    private final AuditTrail auditTrail;

    public SyncRunner(
            DocumentSource source,
            DocumentAuthoring authoring,
            SecretCipher cipher,
            AuditTrail auditTrail
    ) {
        this.source = source;
        this.authoring = authoring;
        this.cipher = cipher;
        this.auditTrail = auditTrail;
    }

    /**
     * Fetches, plans, and applies.
     *
     * <p>Never throws for an expected failure — an unreachable repository or a bad
     * branch is reported as a {@link SyncStatus#FAILED} outcome, because a webhook
     * has nobody to show a stack trace to and the operator needs the reason on their
     * settings screen.
     *
     * @param actorId who asked, or null when a webhook did
     */
    @Transactional
    public SyncOutcome run(SyncConfiguration configuration, UUID actorId) {
        return run(configuration, actorId, null);
    }

    /**
     * @param revision the exact revision to apply, or null for the configured
     *                 branch. A webhook names the commit it delivered, which is
     *                 both more accurate and immune to a host serving a cached
     *                 archive of the branch — see {@link PushEvent}.
     */
    public SyncOutcome run(SyncConfiguration configuration, UUID actorId, String revision) {
        configuration.recordAttempt();

        if (!configuration.isConfigured()) {
            return record(configuration, SyncOutcome.failed("No repository is configured."), actorId);
        }
        if (!configuration.isEnabled()) {
            return record(configuration, SyncOutcome.failed("Syncing is switched off."), actorId);
        }

        SourceSnapshot snapshot;
        try {
            String token = cipher.decrypt(configuration.getAccessToken()).orElse(null);
            if (configuration.getAccessToken() != null && token == null) {
                // The signing secret was rotated, so the stored token cannot be read.
                return record(configuration, SyncOutcome.failed(
                        "The stored access token can no longer be read, most likely because "
                        + "DEVFORGE_JWT_SECRET was changed. Enter it again to reconnect."),
                        actorId);
            }
            snapshot = source.fetch(configuration, token, revision);
        } catch (SourceUnavailableException e) {
            log.warn("Sync fetch failed for workspace {}: {}",
                    configuration.getWorkspaceId(), e.getMessage());
            return record(configuration, SyncOutcome.failed(e.getMessage()), actorId);
        }

        Set<String> existing = new HashSet<>(authoring.slugsIn(configuration.getWorkspaceId()));
        SyncPlan plan = SyncPlan.from(
                snapshot,
                existing,
                configuration.getDocumentPath(),
                configuration.getDefaultType(),
                configuration.getDeletionPolicy());

        // The safety valve. A mistyped document path matches nothing, and under the
        // archive policy that would withdraw every page in the workspace in one go.
        // A repository that genuinely contains no documentation is indistinguishable
        // from a misconfiguration, so the destructive reading is refused.
        if (plan.documents().isEmpty() && !existing.isEmpty()
                && configuration.getDeletionPolicy() != DeletionPolicy.IGNORE) {
            return record(configuration, SyncOutcome.failed(
                    ("Found no documentation at '%s' on branch %s, which would have withdrawn "
                     + "all %d pages in this workspace. Check the path and branch; nothing was "
                     + "changed.")
                            .formatted(
                                    configuration.getDocumentPath().isEmpty()
                                            ? "the repository root"
                                            : configuration.getDocumentPath(),
                                    configuration.getBranch(),
                                    existing.size())),
                    actorId);
        }

        return record(configuration, apply(configuration, plan, snapshot, actorId), actorId);
    }

    private SyncOutcome apply(
            SyncConfiguration configuration,
            SyncPlan plan,
            SourceSnapshot snapshot,
            UUID actorId
    ) {
        UUID workspaceId = configuration.getWorkspaceId();
        List<String> problems = new ArrayList<>(plan.problems());
        int created = 0;
        int updated = 0;
        int unchanged = 0;

        // First pass: every document exists before any link is resolved, so a file
        // may point at one that appears later in the same import. Documentation is
        // written as a graph, not in dependency order.
        for (SyncPlan.PlannedDocument planned : plan.documents()) {
            DocumentDraft draft = planned.draft();
            try {
                switch (authoring.upsert(workspaceId, draft, actorId, AuthoringOrigin.SYNC)) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                }
            } catch (RuntimeException e) {
                // One unusable file must not abandon the rest. Recording which one
                // failed is more useful than a single opaque failure for the sync.
                problems.add("%s: could not be applied (%s)".formatted(draft.slug(), e.getMessage()));
            }
        }

        // Second pass: the typed links, now that every target can be resolved.
        int linked = 0;
        int unlinked = 0;
        for (SyncPlan.PlannedDocument planned : plan.documents()) {
            if (!planned.managesReferences()) {
                // The file declares no relationships, so the repository is not
                // managing this page's links and must not remove any.
                continue;
            }
            try {
                ReferenceOutcome outcome = authoring.replaceReferences(
                        workspaceId,
                        planned.draft().slug(),
                        planned.references(),
                        actorId,
                        AuthoringOrigin.SYNC);
                linked += outcome.added();
                unlinked += outcome.removed();
                outcome.unresolved().forEach(target -> problems.add(
                        "%s: links to '%s', which is not a document here"
                                .formatted(planned.draft().slug(), target)));
            } catch (RuntimeException e) {
                problems.add("%s: links could not be applied (%s)"
                        .formatted(planned.draft().slug(), e.getMessage()));
            }
        }

        int archived = 0;
        for (String slug : plan.archived()) {
            try {
                boolean changed = configuration.getDeletionPolicy() == DeletionPolicy.DELETE
                        ? authoring.deleteBySlug(workspaceId, slug, actorId, AuthoringOrigin.SYNC)
                        : authoring.archiveBySlug(workspaceId, slug, actorId, AuthoringOrigin.SYNC);
                if (changed) {
                    archived++;
                }
            } catch (RuntimeException e) {
                problems.add("%s: could not be withdrawn (%s)".formatted(slug, e.getMessage()));
            }
        }

        SyncStatus status = problems.isEmpty() ? SyncStatus.OK : SyncStatus.PARTIAL;
        return new SyncOutcome(
                status,
                snapshot.ref(),
                created,
                updated,
                archived,
                unchanged,
                List.copyOf(problems),
                summarise(status, created, updated, archived, unchanged, linked, unlinked,
                        problems.size()));
    }

    private static String summarise(
            SyncStatus status,
            int created,
            int updated,
            int archived,
            int unchanged,
            int linked,
            int unlinked,
            int problems
    ) {
        StringBuilder counts = new StringBuilder("%d created, %d updated, %d withdrawn, %d unchanged"
                .formatted(created, updated, archived, unchanged));
        // Only mentioned when links actually moved, so an ordinary sync of prose does
        // not report a graph that nobody is maintaining.
        if (linked > 0 || unlinked > 0) {
            counts.append(", %d links added, %d removed".formatted(linked, unlinked));
        }
        if (status == SyncStatus.PARTIAL) {
            counts.append(", %d problem(s)".formatted(problems));
        }
        return counts.toString();
    }

    /** Stores the outcome on the configuration and records it in the audit log. */
    private SyncOutcome record(SyncConfiguration configuration, SyncOutcome outcome, UUID actorId) {
        if (outcome.status() == SyncStatus.FAILED) {
            configuration.recordFailure(outcome.message());
        } else {
            configuration.recordSuccess(
                    outcome.status(),
                    outcome.ref(),
                    outcome.message(),
                    outcome.created(),
                    outcome.updated(),
                    outcome.archived(),
                    outcome.unchanged(),
                    outcome.problems());
        }

        auditTrail.record(actorId, AuditEntry
                .of(AuditAction.WORKSPACE_SYNCED, AuditTargetType.WORKSPACE)
                .target(configuration.getWorkspaceId(), configuration.getRepositoryUrl())
                .inWorkspace(configuration.getWorkspaceId())
                .with("status", outcome.status())
                .with("ref", outcome.ref())
                .with("created", outcome.created())
                .with("updated", outcome.updated())
                .with("withdrawn", outcome.archived())
                // A webhook has no signed-in user; saying so beats an empty actor.
                .with("trigger", actorId == null ? "webhook" : "manual")
                .with("message", outcome.message()));

        return outcome;
    }
}
