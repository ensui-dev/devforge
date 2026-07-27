package com.devforge.document.contract;

/**
 * Where an authored change came from.
 *
 * <p>Recorded on the revision and in the audit log, so history distinguishes
 * someone typing in the editor from a git push arriving. Without it a synced
 * change would be indistinguishable from a hand edit, and "why did this page
 * change?" would have no answer.
 */
public enum AuthoringOrigin {
    /** A person editing through the API or the interface. */
    DIRECT,
    /** Applied from an external source of truth, such as a git repository. */
    SYNC
}
