package com.devforge.document.application;

import com.devforge.document.contract.ReferenceType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDocumentReferenceRequest(
        @NotNull UUID targetDocumentId,
        @NotNull ReferenceType referenceType
) {
}
