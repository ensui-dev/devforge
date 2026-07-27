package com.devforge.document.contract;

/**
 * A typed link declared by an external source, before its target has been resolved.
 *
 * <p>Names the target by slug rather than by id, for the same reason
 * {@link DocumentDraft} does: a markdown file has no idea what internal identifier
 * the page it points at was given, and should not have to.
 */
public record DeclaredReference(ReferenceType referenceType, String targetSlug) {
}
