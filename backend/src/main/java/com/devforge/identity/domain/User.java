package com.devforge.identity.domain;

import com.devforge.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /**
     * URL-safe, unique name for this account. It namespaces the workspaces this
     * user owns, so their public documentation lives at {@code /docs/{handle}/…}.
     */
    @Column(nullable = false, length = 39)
    private String handle;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * Whether this account may configure the instance.
     *
     * <p>Separate from workspace roles: an instance admin operates the deployment
     * and gains no access to anyone's content by virtue of it.
     */
    @Column(name = "instance_admin", nullable = false)
    private boolean instanceAdmin = false;

    protected User() {
    }

    /**
     * @param email        must already be normalised via {@link #normalizeEmail}
     * @param passwordHash an encoded hash; this type never sees a raw password
     */
    public User(String email, String displayName, String handle, String passwordHash) {
        this.email = email;
        this.displayName = displayName;
        this.handle = handle;
        this.passwordHash = passwordHash;
    }

    /**
     * Addresses are compared case-insensitively, so they are stored folded. The
     * unique index on {@code email} then enforces "one account per address"
     * without needing a functional index.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHandle() {
        return handle;
    }

    public void changeHandle(String handle) {
        this.handle = handle;
    }

    /**
     * Suggests a handle from an email address, matching the pattern the column
     * enforces. Callers must still resolve collisions.
     */
    public static String suggestHandle(String email) {
        String local = email == null ? "" : email.split("@")[0];
        String cleaned = local.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 32).replaceAll("-+$", "");
        }
        return cleaned.isEmpty() ? "user" : cleaned;
    }

    public boolean isInstanceAdmin() {
        return instanceAdmin;
    }

    public void grantInstanceAdmin() {
        this.instanceAdmin = true;
    }

    public void revokeInstanceAdmin() {
        this.instanceAdmin = false;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
