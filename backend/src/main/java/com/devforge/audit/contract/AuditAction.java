package com.devforge.audit.contract;

/**
 * What happened.
 *
 * <p>An enum rather than a free string so the log is queryable and a typo cannot
 * quietly create a category nobody filters on. Names are past tense, because an
 * audit entry records something that already happened.
 */
public enum AuditAction {

    WORKSPACE_CREATED,
    WORKSPACE_UPDATED,
    WORKSPACE_DELETED,
    WORKSPACE_PUBLISHED,
    WORKSPACE_UNPUBLISHED,
    /** Documentation applied from a git repository. */
    WORKSPACE_SYNCED,
    /** A sync was configured, reconfigured, or disconnected. */
    SYNC_CONFIGURED,

    MEMBER_ADDED,
    MEMBER_ROLE_CHANGED,
    MEMBER_REMOVED,

    DOCUMENT_CREATED,
    DOCUMENT_UPDATED,
    DOCUMENT_DELETED,
    DOCUMENT_RESTORED,
    DOCUMENT_LINKED,
    DOCUMENT_UNLINKED,

    BOARD_CREATED,
    BOARD_UPDATED,
    BOARD_DELETED,
    COLUMN_CREATED,
    COLUMN_UPDATED,
    COLUMN_DELETED,

    TASK_CREATED,
    TASK_UPDATED,
    TASK_MOVED,
    TASK_DELETED,
    TASK_DOCUMENT_LINKED,
    TASK_DOCUMENT_UNLINKED,

    INSTANCE_SET_UP,
    INSTANCE_SETTINGS_CHANGED,
    INSTANCE_ADMIN_GRANTED,
    INSTANCE_ADMIN_REVOKED,
    ACCOUNT_CREATED
}
