package com.devforge.shared.exception;

/**
 * The caller is known and may see the resource, but lacks the role required for
 * this particular operation.
 *
 * <p>Used only where hiding the resource would be misleading. When the caller is
 * not a member of a workspace at all, services raise
 * {@link ResourceNotFoundException} instead so that membership cannot be probed.
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }
}
