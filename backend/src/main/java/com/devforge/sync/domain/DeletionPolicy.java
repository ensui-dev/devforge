package com.devforge.sync.domain;

/**
 * What happens to a document whose file has disappeared from the repository.
 *
 * <p>The default is deliberately the cautious one. Deleting is what git means by a
 * removed file, but a mis-set {@code documentPath} makes every file look removed at
 * once, and archiving is recoverable where deleting is not.
 */
public enum DeletionPolicy {
    /** Mark it internal: gone from published documentation, history intact. */
    ARCHIVE,
    /** Remove it, its revisions, and its links. */
    DELETE,
    /** Leave it alone. For a repository that holds only some of the documentation. */
    IGNORE
}
