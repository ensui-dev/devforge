package com.devforge.sync.domain;

/** How the last attempt went. */
public enum SyncStatus {
    OK,
    /** Applied, but some files could not be read. The rest went through. */
    PARTIAL,
    FAILED
}
