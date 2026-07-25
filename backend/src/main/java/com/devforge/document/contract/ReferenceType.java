package com.devforge.document.contract;

/**
 * The meaning of a directed edge between two documents.
 *
 * <p>Typing the edges is what lets the knowledge base answer questions rather
 * than merely cross-link: "what breaks if this changes" is a {@link #DEPENDS_ON}
 * traversal, not a free-text search.
 */
public enum ReferenceType {

    /** Loose association, no implied direction of dependency. */
    RELATED,

    /** The source relies on the target; changing the target may invalidate it. */
    DEPENDS_ON,

    /** The source realises a design or specification described by the target. */
    IMPLEMENTS,

    /** The source describes the subject that the target defines. */
    DOCUMENTS,

    /** The source replaces the target, which is kept for history. */
    SUPERSEDES
}
