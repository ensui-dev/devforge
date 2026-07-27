package com.devforge.sync.domain;

import com.devforge.document.contract.DocumentType;
import com.devforge.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Where a workspace's documentation comes from, and how the last attempt went.
 *
 * <p>The credential fields hold ciphertext, never plaintext — see
 * {@link com.devforge.shared.security.SecretCipher}. Nothing in this class decrypts
 * anything; it stores what it is given and hands it back, so an entity dumped into
 * a log or a stack trace reveals nothing.
 */
@Entity
@Table(name = "sync_configurations")
public class SyncConfiguration extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "repository_url", nullable = false, length = 500)
    private String repositoryUrl;

    @Column(nullable = false, length = 255)
    private String branch;

    @Column(name = "document_path", nullable = false, length = 500)
    private String documentPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_type", nullable = false, length = 32)
    private DocumentType defaultType;

    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_policy", nullable = false, length = 16)
    private DeletionPolicy deletionPolicy;

    /** Ciphertext. Empty means the repository is public. */
    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    /** Ciphertext of the HMAC key the git host signs its webhooks with. */
    @Column(name = "webhook_secret", columnDefinition = "text")
    private String webhookSecret;

    @Column(name = "webhook_id", nullable = false, unique = true)
    private UUID webhookId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "last_succeeded_at")
    private Instant lastSucceededAt;

    @Column(name = "last_ref", length = 255)
    private String lastRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", length = 16)
    private SyncStatus lastStatus;

    @Column(name = "last_message", columnDefinition = "text")
    private String lastMessage;

    @Column(name = "last_created", nullable = false)
    private int lastCreated;

    @Column(name = "last_updated", nullable = false)
    private int lastUpdated;

    @Column(name = "last_archived", nullable = false)
    private int lastArchived;

    @Column(name = "last_unchanged", nullable = false)
    private int lastUnchanged;

    protected SyncConfiguration() {
        // for JPA
    }

    public SyncConfiguration(UUID workspaceId) {
        this.workspaceId = workspaceId;
        this.webhookId = UUID.randomUUID();
        this.branch = "main";
        this.documentPath = "";
        this.defaultType = DocumentType.GENERAL;
        this.deletionPolicy = DeletionPolicy.ARCHIVE;
        this.repositoryUrl = "";
    }

    /**
     * Applies the operator's settings.
     *
     * <p>Credentials are handled separately, because a form that echoes them back
     * cannot distinguish "unchanged" from "cleared" — see {@link #setAccessToken}.
     */
    public void configure(
            String repositoryUrl,
            String branch,
            String documentPath,
            DocumentType defaultType,
            DeletionPolicy deletionPolicy,
            boolean enabled
    ) {
        this.repositoryUrl = repositoryUrl.trim();
        this.branch = branch == null || branch.isBlank() ? "main" : branch.trim();
        this.documentPath = normalisePath(documentPath);
        this.defaultType = defaultType == null ? DocumentType.GENERAL : defaultType;
        this.deletionPolicy = deletionPolicy == null ? DeletionPolicy.ARCHIVE : deletionPolicy;
        this.enabled = enabled;
    }

    /**
     * Leading and trailing slashes removed, so {@code /docs/} and {@code docs} mean
     * the same thing and path comparison later does not have to care.
     */
    private static String normalisePath(String path) {
        if (path == null) {
            return "";
        }
        return path.trim().replaceAll("^/+", "").replaceAll("/+$", "");
    }

    /** @param ciphertext already encrypted, or null to clear */
    public void setAccessToken(String ciphertext) {
        this.accessToken = ciphertext;
    }

    public void setWebhookSecret(String ciphertext) {
        this.webhookSecret = ciphertext;
    }

    /** Invalidates the published webhook URL by minting a new id. */
    public void rotateWebhookId() {
        this.webhookId = UUID.randomUUID();
    }

    public void recordAttempt() {
        this.lastAttemptedAt = now();
    }

    public void recordSuccess(
            SyncStatus status,
            String ref,
            String message,
            int created,
            int updated,
            int archived,
            int unchanged
    ) {
        this.lastStatus = status;
        this.lastRef = ref;
        this.lastMessage = message;
        this.lastCreated = created;
        this.lastUpdated = updated;
        this.lastArchived = archived;
        this.lastUnchanged = unchanged;
        this.lastSucceededAt = now();
    }

    /**
     * Records a failure without disturbing {@code lastSucceededAt}.
     *
     * <p>Keeping the last success separate from the last attempt is what lets the
     * interface say "failing since Tuesday" rather than only "failed".
     */
    public void recordFailure(String message) {
        this.lastStatus = SyncStatus.FAILED;
        this.lastMessage = message;
        this.lastCreated = 0;
        this.lastUpdated = 0;
        this.lastArchived = 0;
        this.lastUnchanged = 0;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getBranch() {
        return branch;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public DocumentType getDefaultType() {
        return defaultType;
    }

    public DeletionPolicy getDeletionPolicy() {
        return deletionPolicy;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public UUID getWebhookId() {
        return webhookId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConfigured() {
        return repositoryUrl != null && !repositoryUrl.isBlank();
    }

    public Instant getLastAttemptedAt() {
        return lastAttemptedAt;
    }

    public Instant getLastSucceededAt() {
        return lastSucceededAt;
    }

    public String getLastRef() {
        return lastRef;
    }

    public SyncStatus getLastStatus() {
        return lastStatus;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public int getLastCreated() {
        return lastCreated;
    }

    public int getLastUpdated() {
        return lastUpdated;
    }

    public int getLastArchived() {
        return lastArchived;
    }

    public int getLastUnchanged() {
        return lastUnchanged;
    }
}
