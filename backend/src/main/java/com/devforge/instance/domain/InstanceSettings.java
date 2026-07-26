package com.devforge.instance.domain;

import com.devforge.instance.contract.RegistrationMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * How this deployment is configured: its identity, who may join, and what it
 * publishes.
 *
 * <p>A single row, keyed by a constant. It does not extend {@code BaseEntity}
 * because it has no UUID identity — there is exactly one instance, and giving it a
 * random id would imply otherwise.
 */
@Entity
@Table(name = "instance_settings")
public class InstanceSettings {

    /** The constant key. There is one instance, so there is one row. */
    public static final Boolean KEY = Boolean.TRUE;

    @Id
    private Boolean id = KEY;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 200)
    private String tagline;

    @Column(name = "logo_mark", nullable = false, length = 8)
    private String logoMark = "⌁";

    @Column(name = "logo_image", columnDefinition = "TEXT")
    private String logoImage;

    @Column(name = "accent_color", length = 7)
    private String accentColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_mode", nullable = false, length = 20)
    private RegistrationMode registrationMode = RegistrationMode.OPEN;

    @Column(name = "allowed_email_domains", columnDefinition = "TEXT")
    private String allowedEmailDomains;

    @Column(name = "public_docs_enabled", nullable = false)
    private boolean publicDocsEnabled = true;

    @Column(name = "handbook_path", length = 210)
    private String handbookPath;

    @Column(name = "public_base_url", length = 200)
    private String publicBaseUrl;

    @Column(name = "setup_completed_at")
    private Instant setupCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected InstanceSettings() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public String getName() {
        return name;
    }

    public String getTagline() {
        return tagline;
    }

    public String getLogoMark() {
        return logoMark;
    }

    public String getLogoImage() {
        return logoImage;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public RegistrationMode getRegistrationMode() {
        return registrationMode;
    }

    public String getAllowedEmailDomains() {
        return allowedEmailDomains;
    }

    public boolean isPublicDocsEnabled() {
        return publicDocsEnabled;
    }

    public String getHandbookPath() {
        return handbookPath;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public Instant getSetupCompletedAt() {
        return setupCompletedAt;
    }

    public boolean isConfigured() {
        return setupCompletedAt != null;
    }

    /** Applies the identity and policy an operator chose. */
    public void configure(
            String name,
            String tagline,
            String logoMark,
            String logoImage,
            String accentColor,
            RegistrationMode registrationMode,
            String allowedEmailDomains,
            boolean publicDocsEnabled,
            String handbookPath,
            String publicBaseUrl
    ) {
        this.name = name;
        this.tagline = tagline;
        this.logoMark = logoMark == null || logoMark.isBlank() ? "⌁" : logoMark.trim();
        this.logoImage = logoImage;
        this.accentColor = accentColor == null || accentColor.isBlank()
                ? null
                : accentColor.trim().toLowerCase(Locale.ROOT);
        this.registrationMode = registrationMode == null ? RegistrationMode.OPEN : registrationMode;
        this.allowedEmailDomains = normalizeDomains(allowedEmailDomains);
        this.publicDocsEnabled = publicDocsEnabled;
        this.handbookPath = handbookPath == null || handbookPath.isBlank()
                ? null
                : handbookPath.trim().replaceAll("^/+|/+$", "");
        this.publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? null
                : publicBaseUrl.trim().replaceAll("/+$", "");
    }

    /** Closes the one-shot setup endpoint. Recorded once and never reset. */
    public void completeSetup() {
        if (setupCompletedAt == null) {
            setupCompletedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    /**
     * @return whether this address may register under the current policy
     */
    public boolean permitsRegistrationOf(String email) {
        return switch (registrationMode) {
            case OPEN -> true;
            case CLOSED -> false;
            case RESTRICTED -> domainOf(email)
                    .map(domain -> allowedDomains().contains(domain))
                    .orElse(false);
        };
    }

    public List<String> allowedDomains() {
        if (allowedEmailDomains == null || allowedEmailDomains.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedEmailDomains.split(","))
                .map(String::trim)
                .filter(domain -> !domain.isEmpty())
                .toList();
    }

    private static java.util.Optional<String> domainOf(String email) {
        if (email == null || !email.contains("@")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase(Locale.ROOT));
    }

    private static String normalizeDomains(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = Arrays.stream(raw.split("[,\\s]+"))
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT).replaceAll("^@", ""))
                .filter(domain -> !domain.isEmpty())
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
