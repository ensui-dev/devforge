package com.devforge.sync.application;

import com.devforge.sync.domain.SyncStatus;

import java.util.List;

/**
 * What a sync did.
 *
 * @param problems files that could not be used. A sync with problems still applies
 *                 everything it understood — one malformed file should not block a
 *                 hundred good ones.
 */
public record SyncOutcome(
        SyncStatus status,
        String ref,
        int created,
        int updated,
        int archived,
        int unchanged,
        List<String> problems,
        String message
) {

    public static SyncOutcome failed(String message) {
        return new SyncOutcome(SyncStatus.FAILED, null, 0, 0, 0, 0, List.of(), message);
    }
}
