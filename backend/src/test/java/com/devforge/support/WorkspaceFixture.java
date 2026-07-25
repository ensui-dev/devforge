package com.devforge.support;

import java.util.UUID;

/**
 * Identifiers created by a test's setup, passed around instead of a handful of
 * loose {@code String} fields.
 */
public record WorkspaceFixture(UUID workspaceId, String slug) {
}
