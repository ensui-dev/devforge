package com.devforge.sync.api;

import com.devforge.sync.application.GitRepositoryResponse;
import com.devforge.sync.application.SyncOutcome;
import com.devforge.sync.application.SyncService;
import com.devforge.sync.application.SyncSettingsRequest;
import com.devforge.sync.application.SyncSettingsResponse;
import com.devforge.sync.application.WebhookSignature;
import com.devforge.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@Tag(name = "Git sync")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/api/workspaces/{workspaceId}/sync")
    @Operation(summary = "How this workspace's documentation is synced (workspace admin)")
    public SyncSettingsResponse describe(
            @PathVariable UUID workspaceId,
            @CurrentUser UUID userId
    ) {
        return syncService.describe(workspaceId, userId);
    }

    @GetMapping("/api/workspaces/{workspaceId}/git")
    @Operation(summary = "The git repository DevForge hosts for this workspace")
    public GitRepositoryResponse describeRepository(
            @PathVariable UUID workspaceId,
            @CurrentUser UUID userId
    ) {
        return syncService.describeRepository(workspaceId, userId);
    }

    @PutMapping("/api/workspaces/{workspaceId}/sync")
    @Operation(summary = "Point this workspace at a git repository (workspace admin)")
    public SyncSettingsResponse configure(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody SyncSettingsRequest request,
            @CurrentUser UUID userId
    ) {
        return syncService.configure(workspaceId, request, userId);
    }

    /**
     * Runs a sync immediately.
     *
     * <p>Exists so an operator can tell whether their settings work without pushing
     * a commit. Returns the outcome rather than a 202: the whole value is the
     * feedback.
     */
    @PostMapping("/api/workspaces/{workspaceId}/sync/run")
    @Operation(summary = "Sync now, and report what happened (workspace admin)")
    public SyncSettingsResponse syncNow(
            @PathVariable UUID workspaceId,
            @CurrentUser UUID userId
    ) {
        return syncService.syncNow(workspaceId, userId);
    }

    /**
     * Generates a webhook signing secret and returns it once.
     *
     * <p>The only response that ever contains it in the clear. It is stored
     * encrypted, so there is no second chance to read it — which is why the interface
     * tells the operator to copy it now.
     */
    @PostMapping("/api/workspaces/{workspaceId}/sync/secret")
    @Operation(summary = "Generate a webhook secret, shown once (workspace admin)")
    public Map<String, String> regenerateSecret(
            @PathVariable UUID workspaceId,
            @CurrentUser UUID userId
    ) {
        return Map.of("webhookSecret", syncService.regenerateWebhookSecret(workspaceId, userId));
    }

    @PostMapping("/api/workspaces/{workspaceId}/sync/rotate-url")
    @Operation(summary = "Mint a new webhook URL, invalidating the old one (workspace admin)")
    public SyncSettingsResponse rotateUrl(
            @PathVariable UUID workspaceId,
            @CurrentUser UUID userId
    ) {
        return syncService.rotateWebhookUrl(workspaceId, userId);
    }

    @DeleteMapping("/api/workspaces/{workspaceId}/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disconnect the repository and forget its credentials (workspace admin)")
    public void disconnect(
            @PathVariable UUID workspaceId,
            @CurrentUser UUID userId
    ) {
        syncService.disconnect(workspaceId, userId);
    }

    /**
     * Where a git host delivers a push.
     *
     * <p>Unauthenticated, because a git host has no DevForge session. The HMAC
     * signature over the raw body is what authorises the call, so the body is taken
     * as {@code byte[]}: parsing and re-serialising the JSON would change whitespace
     * and key order, and the signature would never match.
     *
     * <p>Every rejection answers 404, including a valid id with a bad signature.
     * Distinguishing "no such webhook" from "wrong secret" would let someone with the
     * URL confirm that a workspace syncs, which is more than they should learn.
     */
    @PostMapping("/api/public/sync/{webhookId}")
    @SecurityRequirements
    @Operation(summary = "Webhook endpoint for a git host; verified by HMAC signature")
    public ResponseEntity<Map<String, Object>> webhook(
            @PathVariable UUID webhookId,
            @RequestBody(required = false) byte[] body,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String githubSignature,
            @RequestHeader(name = "X-Gitea-Signature", required = false) String giteaSignature,
            @RequestHeader(name = "X-Forgejo-Signature", required = false) String forgejoSignature
    ) {
        Optional<SyncService.VerifiableWebhook> found = syncService.findForWebhook(webhookId);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SyncService.VerifiableWebhook webhook = found.get();
        byte[] payload = body == null ? new byte[0] : body;
        String presented = Optional.ofNullable(githubSignature)
                .or(() -> Optional.ofNullable(giteaSignature))
                .or(() -> Optional.ofNullable(forgejoSignature))
                .orElse(null);

        if (!WebhookSignature.matches(payload, webhook.secret(), presented)) {
            log.warn("Rejected a webhook delivery for workspace {}: signature did not verify",
                    webhook.configuration().getWorkspaceId());
            return ResponseEntity.notFound().build();
        }

        SyncOutcome outcome = syncService.syncFromWebhook(webhook.configuration());

        // 200 even for a failed sync: the delivery was accepted and understood, and a
        // git host retrying because DevForge could not reach the repository would not
        // help. The outcome is on the settings screen and in the audit log.
        return ResponseEntity.ok(Map.of(
                "status", outcome.status().name(),
                "created", outcome.created(),
                "updated", outcome.updated(),
                "withdrawn", outcome.archived(),
                "unchanged", outcome.unchanged(),
                "message", outcome.message() == null ? "" : outcome.message()));
    }
}
