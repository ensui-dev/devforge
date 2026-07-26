package com.devforge.document.application;

import com.devforge.document.contract.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateDocumentRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank
        @Size(max = 200)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be lowercase alphanumeric with hyphens")
        String slug,
        String content,
        @NotNull DocumentType documentType,
        /** Held back from the public site even when the workspace is published. */
        Boolean internal
) {

    public boolean isInternal() {
        return Boolean.TRUE.equals(internal);
    }
}
