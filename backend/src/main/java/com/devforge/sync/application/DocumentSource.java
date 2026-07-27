package com.devforge.sync.application;

import com.devforge.sync.domain.SyncConfiguration;

/**
 * Fetches a repository's documentation.
 *
 * <p>An interface rather than a concrete fetcher for two reasons. It keeps the
 * planning and applying logic — which is where the correctness lives — testable
 * without a network or a git host. And it means the transport can change without
 * touching that logic: the shipped implementation downloads an archive over HTTPS,
 * but a real git client dropping in later would implement this same method.
 *
 * @see com.devforge.sync.infrastructure.ArchiveDocumentSource
 */
public interface DocumentSource {

    /**
     * @param accessToken decrypted, or null for a public repository
     * @param revision    the exact revision to read, or null for the configured
     *                    branch. A webhook names the commit it just delivered,
     *                    which a branch name cannot be relied on to resolve to —
     *                    see {@link PushEvent}.
     * @throws SourceUnavailableException when the repository cannot be read; the
     *         message is shown to the operator, so it must say something useful
     */
    SourceSnapshot fetch(SyncConfiguration configuration, String accessToken, String revision);
}
