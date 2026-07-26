package com.devforge.document.api;

import com.devforge.document.application.CreateDocumentRequest;
import com.devforge.document.application.DocumentHistoryService;
import com.devforge.document.application.DocumentResponse;
import com.devforge.document.application.DocumentRevisionResponse;
import com.devforge.document.application.DocumentService;
import com.devforge.document.application.DocumentSummaryResponse;
import com.devforge.document.application.UpdateDocumentRequest;
import com.devforge.document.contract.DocumentType;
import com.devforge.shared.application.PageResponse;
import com.devforge.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/documents")
@Validated
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentHistoryService historyService;

    public DocumentController(
            DocumentService documentService,
            DocumentHistoryService historyService
    ) {
        this.documentService = documentService;
        this.historyService = historyService;
    }

    @GetMapping
    @Operation(summary = "List documents, optionally filtered by type")
    public PageResponse<DocumentSummaryResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @CurrentUser UUID userId
    ) {
        return documentService.findByWorkspace(
                workspaceId, userId, documentType, PageRequest.of(page, size));
    }

    @GetMapping("/search")
    @Operation(summary = "Full-text search across the workspace's documentation")
    public PageResponse<DocumentSummaryResponse> search(
            @PathVariable UUID workspaceId,
            @RequestParam("q") @NotBlank String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @CurrentUser UUID userId
    ) {
        return documentService.search(workspaceId, userId, query, PageRequest.of(page, size));
    }

    @GetMapping("/by-slug/{slug}")
    @Operation(summary = "Get a document by its slug, for stable deep links")
    public DocumentResponse getBySlug(
            @PathVariable UUID workspaceId,
            @PathVariable String slug,
            @CurrentUser UUID userId
    ) {
        return documentService.findBySlug(workspaceId, slug, userId);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Get a document with its full body")
    public DocumentResponse get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @CurrentUser UUID userId
    ) {
        return documentService.findById(workspaceId, documentId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a document (MEMBER)")
    public DocumentResponse create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateDocumentRequest request,
            @CurrentUser UUID userId
    ) {
        return documentService.create(workspaceId, request, userId);
    }

    @PutMapping("/{documentId}")
    @Operation(summary = "Replace a document's content and metadata (MEMBER)")
    public DocumentResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @Valid @RequestBody UpdateDocumentRequest request,
            @CurrentUser UUID userId
    ) {
        return documentService.update(workspaceId, documentId, request, userId);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document and its references (MEMBER)")
    public void delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @CurrentUser UUID userId
    ) {
        documentService.delete(workspaceId, documentId, userId);
    }

    /**
     * What this document has said over time, newest first.
     *
     * <p>Bodies are omitted; a list of fifty revisions of a long page would
     * otherwise ship the whole document fifty times to render a list of dates.
     */
    @GetMapping("/{documentId}/revisions")
    @Operation(summary = "List a document's revisions")
    public PageResponse<DocumentRevisionResponse> revisions(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @CurrentUser UUID userId
    ) {
        return historyService.history(
                workspaceId, documentId, userId, PageRequest.of(page, size));
    }

    @GetMapping("/{documentId}/revisions/{revision}")
    @Operation(summary = "Read one revision in full, for viewing or diffing")
    public DocumentRevisionResponse revision(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @PathVariable @Min(1) int revision,
            @CurrentUser UUID userId
    ) {
        return historyService.revision(workspaceId, documentId, revision, userId);
    }

    /**
     * Puts an earlier revision's content back.
     *
     * <p>Appends a new revision rather than rewinding, so the restore itself is
     * visible in history and can be undone the same way.
     */
    @PostMapping("/{documentId}/revisions/{revision}/restore")
    @Operation(summary = "Restore an earlier revision as a new one")
    public DocumentResponse restore(
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @PathVariable @Min(1) int revision,
            @CurrentUser UUID userId
    ) {
        return historyService.restore(workspaceId, documentId, revision, userId);
    }
}
