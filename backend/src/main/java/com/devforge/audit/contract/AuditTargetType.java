package com.devforge.audit.contract;

/** The kind of thing an event happened to. */
public enum AuditTargetType {
    WORKSPACE,
    MEMBER,
    DOCUMENT,
    BOARD,
    COLUMN,
    TASK,
    INSTANCE,
    ACCOUNT
}
