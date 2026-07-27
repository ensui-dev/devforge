package com.devforge.document.domain;

/** Why a revision exists. */
public enum RevisionReason {
    /** The document was created; this is revision 1. */
    CREATED,
    UPDATED,
    /** Produced by restoring an earlier revision, which never rewinds history. */
    RESTORED,
    /**
     * Applied from an external source of truth, such as a git repository.
     *
     * <p>Distinguished from {@link #UPDATED} so history can answer "why did this
     * page change?" — a push arriving reads very differently from someone typing.
     */
    SYNCED
}
