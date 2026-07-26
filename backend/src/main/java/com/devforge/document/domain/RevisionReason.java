package com.devforge.document.domain;

/** Why a revision exists. */
public enum RevisionReason {
    /** The document was created; this is revision 1. */
    CREATED,
    UPDATED,
    /** Produced by restoring an earlier revision, which never rewinds history. */
    RESTORED
}
