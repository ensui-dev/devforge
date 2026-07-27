package com.devforge.sync.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where hosted git repositories live.
 *
 * <p>This is the first state DevForge keeps outside PostgreSQL. Everything else —
 * including uploaded logos, deliberately — lives in the database so that
 * {@code pg_dump} captures an instance whole. Repositories cannot reasonably follow
 * that rule: they are packfiles, written by a protocol that expects a filesystem.
 *
 * <p>So a self-hosted instance now has two things to back up, and the documentation
 * says so rather than leaving an operator to discover it after a restore.
 *
 * @param root    directory holding one bare repository per workspace
 * @param enabled whether to serve git over HTTP at all. An instance that only uses
 *                webhook syncing has no reason to expose the protocol.
 */
@Validated
@ConfigurationProperties(prefix = "devforge.git")
public record GitStorageProperties(
        @NotBlank String root,
        boolean enabled
) {

    public GitStorageProperties {
        if (root == null || root.isBlank()) {
            root = "/var/lib/devforge/git";
        }
    }
}
