package com.devforge.document.api;

import com.devforge.document.application.PublicDocsService;
import com.devforge.document.application.PublicDocumentResponse;
import com.devforge.document.application.PublicHandbookResponse;
import com.devforge.document.application.PublishedWorkspaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Documentation published by workspaces, readable without an account.
 *
 * <p>Unauthenticated and read-only. {@code SecurityRequirements} is cleared so the
 * generated API docs do not suggest a token is needed.
 */
@RestController
@RequestMapping("/api/public/docs")
@SecurityRequirements
@Tag(name = "Public documentation")
public class PublicDocsController {

    private final PublicDocsService publicDocsService;

    public PublicDocsController(PublicDocsService publicDocsService) {
        this.publicDocsService = publicDocsService;
    }

    @GetMapping
    @Operation(summary = "List every workspace that has published its documentation")
    public List<PublishedWorkspaceResponse> directory() {
        return publicDocsService.directory();
    }

    @GetMapping("/{handle}")
    @Operation(summary = "List everything one owner has published")
    public OwnerDocsResponse byOwner(@PathVariable String handle) {
        // A bare slug here is very likely a link made before slugs were namespaced,
        // so the response carries the canonical path for the client to redirect to
        // rather than simply 404ing a URL that used to work.
        List<PublishedWorkspaceResponse> workspaces = publicDocsService.byOwner(handle);
        return new OwnerDocsResponse(
                handle,
                workspaces,
                workspaces.isEmpty()
                        ? publicDocsService.canonicalPathForSlug(handle).orElse(null)
                        : null);
    }

    /**
     * @param movedTo where a legacy {@code /docs/{slug}} link now lives, when this
     *                segment is not a handle but does resolve to one published
     *                workspace; null otherwise
     */
    public record OwnerDocsResponse(
            String handle,
            List<PublishedWorkspaceResponse> workspaces,
            String movedTo
    ) {
    }

    @GetMapping("/{handle}/{workspaceSlug}")
    @Operation(summary = "List the pages of one published workspace")
    public PublicHandbookResponse contents(
            @PathVariable String handle,
            @PathVariable String workspaceSlug
    ) {
        return publicDocsService.tableOfContents(handle, workspaceSlug);
    }

    /**
     * {@code {*documentSlug}} rather than {@code {documentSlug}}: a slug mirrors the
     * folders it came from, so {@code runbooks/consumer-lag} is one slug spanning two
     * path segments. Spring hands it back with a leading slash, which is stripped.
     */
    @GetMapping("/{handle}/{workspaceSlug}/{*documentSlug}")
    @Operation(summary = "Read one published page with its reference graph")
    public PublicDocumentResponse document(
            @PathVariable String handle,
            @PathVariable String workspaceSlug,
            @PathVariable String documentSlug
    ) {
        return publicDocsService.findDocument(handle, workspaceSlug, trimLeadingSlash(documentSlug));
    }

    private static String trimLeadingSlash(String path) {
        return path != null && path.startsWith("/") ? path.substring(1) : path;
    }
}
