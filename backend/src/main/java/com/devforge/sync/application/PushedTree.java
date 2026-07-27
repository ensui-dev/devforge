package com.devforge.sync.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DocumentAuthoring;
import com.devforge.document.contract.DocumentDraft;
import com.devforge.document.contract.DocumentType;
import com.devforge.sync.domain.DeletionPolicy;
import com.devforge.sync.domain.SyncConfiguration;
import com.devforge.sync.domain.SyncConfigurationRepository;
import com.devforge.sync.domain.SyncStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies documentation that arrived by {@code git push}.
 *
 * <p>Shares the planner and the authoring contract with the webhook path, so the two
 * ways documentation can arrive cannot drift apart. What differs is only where the
 * files came from — a pushed tree rather than a downloaded archive — which is why
 * this takes files and the fetching lives elsewhere.
 *
 * <p>A workspace that was never configured for syncing still accepts a push: the
 * repository is hosted here, so there is nothing to configure. The settings only
 * matter for the parts a push cannot state — which folder holds the documentation,
 * and what a deleted file means — so a default configuration is created on the first
 * push, and the operator can adjust it afterwards.
 */
@Service
public class PushedTree {

    private final SyncConfigurationRepository configurations;
    private final DocumentAuthoring authoring;
    private final AuditTrail auditTrail;

    public PushedTree(
            SyncConfigurationRepository configurations,
            DocumentAuthoring authoring,
            AuditTrail auditTrail
    ) {
        this.configurations = configurations;
        this.authoring = authoring;
        this.auditTrail = auditTrail;
    }

    /**
     * @return a one-line summary, sent back down the push connection
     */
    @Transactional
    public String apply(UUID workspaceId, UUID actorId, List<SourceFile> files, String commitId) {
        SyncConfiguration configuration = configurations.findByWorkspaceId(workspaceId)
                .orElseGet(() -> configurations.save(hostedConfiguration(workspaceId)));

        Set<String> existing = new HashSet<>(authoring.slugsIn(workspaceId));
        SyncPlan plan = SyncPlan.from(
                new SourceSnapshot(commitId, files),
                existing,
                configuration.getDocumentPath(),
                configuration.getDefaultType(),
                configuration.getDeletionPolicy());

        // The same refusal the fetching path makes. A push that moves documentation
        // out of the configured folder looks exactly like one that deleted all of
        // it, and withdrawing a workspace's every page is not a guess worth making.
        if (plan.documents().isEmpty() && !existing.isEmpty()
                && configuration.getDeletionPolicy() != DeletionPolicy.IGNORE) {
            String message = ("no documentation found at '%s'; refusing to withdraw all %d pages. "
                    + "Nothing was changed.")
                    .formatted(
                            configuration.getDocumentPath().isEmpty()
                                    ? "the repository root"
                                    : configuration.getDocumentPath(),
                            existing.size());
            configuration.recordAttempt();
            configuration.recordFailure(message);
            record(workspaceId, actorId, commitId, SyncStatus.FAILED, message);
            return message;
        }

        configuration.recordAttempt();
        Applied applied = write(workspaceId, actorId, plan, configuration.getDeletionPolicy());

        SyncStatus status = applied.problems().isEmpty() ? SyncStatus.OK : SyncStatus.PARTIAL;
        String summary = applied.summarise(status);
        configuration.recordSuccess(
                status, commitId, summary,
                applied.created(), applied.updated(), applied.archived(), applied.unchanged(),
                applied.problems());

        record(workspaceId, actorId, commitId, status, summary);
        return summary;
    }

    private Applied write(
            UUID workspaceId,
            UUID actorId,
            SyncPlan plan,
            DeletionPolicy deletionPolicy
    ) {
        List<String> problems = new ArrayList<>(plan.problems());
        int created = 0;
        int updated = 0;
        int unchanged = 0;

        for (SyncPlan.PlannedDocument planned : plan.documents()) {
            DocumentDraft draft = planned.draft();
            try {
                switch (authoring.upsert(workspaceId, draft, actorId, AuthoringOrigin.SYNC)) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                }
            } catch (RuntimeException e) {
                problems.add("%s: could not be applied (%s)".formatted(draft.slug(), e.getMessage()));
            }
        }

        int linked = 0;
        int unlinked = 0;
        for (SyncPlan.PlannedDocument planned : plan.documents()) {
            if (!planned.managesReferences()) {
                continue;
            }
            try {
                DocumentAuthoring.ReferenceOutcome outcome = authoring.replaceReferences(
                        workspaceId, planned.draft().slug(), planned.references(),
                        actorId, AuthoringOrigin.SYNC);
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
                boolean changed = deletionPolicy == DeletionPolicy.DELETE
                        ? authoring.deleteBySlug(workspaceId, slug, actorId, AuthoringOrigin.SYNC)
                        : authoring.archiveBySlug(workspaceId, slug, actorId, AuthoringOrigin.SYNC);
                if (changed) {
                    archived++;
                }
            } catch (RuntimeException e) {
                problems.add("%s: could not be withdrawn (%s)".formatted(slug, e.getMessage()));
            }
        }

        return new Applied(created, updated, archived, unchanged, linked, unlinked, problems);
    }

    private record Applied(
            int created, int updated, int archived, int unchanged,
            int linked, int unlinked, List<String> problems
    ) {
        String summarise(SyncStatus status) {
            StringBuilder text = new StringBuilder(
                    "%d created, %d updated, %d withdrawn, %d unchanged"
                            .formatted(created, updated, archived, unchanged));
            if (linked > 0 || unlinked > 0) {
                text.append(", %d links added, %d removed".formatted(linked, unlinked));
            }
            if (status == SyncStatus.PARTIAL) {
                text.append(", %d problem(s)".formatted(problems.size()));
            }
            return text.toString();
        }
    }

    /** What a workspace gets on its first push, before anyone configures anything. */
    private static SyncConfiguration hostedConfiguration(UUID workspaceId) {
        SyncConfiguration configuration = new SyncConfiguration(workspaceId);
        configuration.configure(
                // Hosted here, so there is no remote to fetch from. The URL is what
                // a clone would use, filled in for display rather than for fetching.
                "",
                GitRepositoryDefaults.BRANCH,
                "",
                DocumentType.GENERAL,
                DeletionPolicy.ARCHIVE,
                true);
        return configuration;
    }

    /** Kept here so the application layer does not reach into infrastructure. */
    private static final class GitRepositoryDefaults {
        static final String BRANCH = "main";
    }

    private void record(UUID workspaceId, UUID actorId, String commitId, SyncStatus status, String summary) {
        auditTrail.record(actorId, AuditEntry
                .of(AuditAction.WORKSPACE_SYNCED, AuditTargetType.WORKSPACE)
                .target(workspaceId, null)
                .inWorkspace(workspaceId)
                .with("status", status)
                .with("ref", commitId)
                .with("trigger", "push")
                .with("message", summary));
    }

    /** Whether a workspace has a sync configuration at all. */
    public Optional<SyncConfiguration> configurationFor(UUID workspaceId) {
        return configurations.findByWorkspaceId(workspaceId);
    }
}
