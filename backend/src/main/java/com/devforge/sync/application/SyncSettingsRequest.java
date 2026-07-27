package com.devforge.sync.application;

import com.devforge.document.contract.DocumentType;
import com.devforge.sync.domain.DeletionPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What an operator sets up.
 *
 * @param accessToken   omit to leave the stored token alone; send an empty string to
 *                      clear it. A form that echoed the token back could not tell
 *                      "unchanged" from "cleared", so absence carries that meaning.
 * @param webhookSecret same convention
 */
public record SyncSettingsRequest(
        @NotBlank
        @Size(max = 500)
        @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL")
        String repositoryUrl,

        @Size(max = 255) String branch,
        @Size(max = 500) String documentPath,
        DocumentType defaultType,
        DeletionPolicy deletionPolicy,
        @NotNull Boolean enabled,

        @Size(max = 500) String accessToken,
        @Size(max = 500) String webhookSecret
) {
}
