package com.devforge.workspace.application;

import jakarta.validation.constraints.NotNull;

/**
 * Publishes or unpublishes a workspace's documentation.
 *
 * @param published true to make the documentation readable by anyone
 */
public record UpdatePublicationRequest(@NotNull Boolean published) {
}
