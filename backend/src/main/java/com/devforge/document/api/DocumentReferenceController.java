package com.devforge.document.api;

import com.devforge.document.application.CreateDocumentReferenceRequest;
import com.devforge.document.application.DocumentReferenceResponse;
import com.devforge.document.application.DocumentReferenceService;
import com.devforge.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/documents/{documentId}/references")
@Tag(name = "Document references")
public class DocumentReferenceController {

    private final DocumentReferenceService referenceService;

    public DocumentReferenceController(DocumentReferenceService referenceService) {
        this.referenceService = referenceService;
    }

    @GetMapping
    @Operation(summary = "List outgoing references and backlinks for a document")
    public List<DocumentReferenceResponse> list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @CurrentUser UUID userId
    ) {
        return referenceService.findReferences(workspaceId, documentId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Link this document to another (MEMBER)")
    public DocumentReferenceResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @Valid @RequestBody CreateDocumentReferenceRequest request,
            @CurrentUser UUID userId
    ) {
        return referenceService.createReference(workspaceId, documentId, request, userId);
    }

    @DeleteMapping("/{referenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a reference declared by this document (MEMBER)")
    public void delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @PathVariable UUID referenceId,
            @CurrentUser UUID userId
    ) {
        referenceService.deleteReference(workspaceId, documentId, referenceId, userId);
    }
}
