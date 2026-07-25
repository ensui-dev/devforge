package com.devforge.task.application;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkDocumentRequest(@NotNull UUID documentId) {
}
