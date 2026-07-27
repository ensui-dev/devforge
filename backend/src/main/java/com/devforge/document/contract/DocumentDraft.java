package com.devforge.document.contract;

/**
 * A document as an external source describes it, before it is reconciled with
 * whatever is already stored.
 *
 * <p>Keyed by slug rather than id: a source outside DevForge — a file in a git
 * repository — has no idea what internal identifier its page was given, and
 * should not have to care. The slug is the stable name both sides agree on.
 */
public record DocumentDraft(
        String slug,
        String title,
        String content,
        DocumentType documentType,
        boolean internal
) {
}
