package com.devforge.workspace.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.DocumentDirectory;
import com.devforge.identity.contract.UserDirectory;
import com.devforge.instance.contract.InstancePolicy;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import com.devforge.workspace.domain.Workspace;
import com.devforge.workspace.domain.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Publishing a workspace's documentation.
 *
 * <p>Kept apart from {@link WorkspaceService} because it is the one operation here
 * that changes who can see the workspace's contents, and that is worth being able
 * to read in isolation.
 */
@Service
@Transactional(readOnly = true)
public class PublicationService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAccess workspaceAccess;
    private final DocumentDirectory documentDirectory;
    private final UserDirectory userDirectory;
    private final InstancePolicy instancePolicy;
    private final AuditTrail auditTrail;

    public PublicationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceAccess workspaceAccess,
            DocumentDirectory documentDirectory,
            UserDirectory userDirectory,
            InstancePolicy instancePolicy,
            AuditTrail auditTrail
    ) {
        this.auditTrail = auditTrail;
        this.workspaceRepository = workspaceRepository;
        this.workspaceAccess = workspaceAccess;
        this.documentDirectory = documentDirectory;
        this.userDirectory = userDirectory;
        this.instancePolicy = instancePolicy;
    }

    /**
     * Describes the current state, including what publishing would expose.
     *
     * <p>Readable by any member, not just admins: everyone writing documents in a
     * published workspace needs to know that is what they are doing.
     */
    public PublicationResponse describe(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        return respond(loadWorkspace(workspaceId));
    }

    @Transactional
    public PublicationResponse update(UUID workspaceId, UpdatePublicationRequest request, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);
        Workspace workspace = loadWorkspace(workspaceId);

        if (request.published()) {
            // An operator can switch public documentation off for the whole
            // instance — a company deployment may want it off entirely.
            if (!instancePolicy.publicDocumentationEnabled()) {
                throw new DomainValidationException(
                        "Public documentation is switched off for this instance.");
            }
            // Publishing an empty workspace would serve a site with nothing on it,
            // which reads as broken rather than as a deliberate state.
            if (documentDirectory.countByVisibility(workspaceId).publicPages() == 0) {
                throw new DomainValidationException(
                        "Write at least one document that is not marked internal before publishing.");
            }
            workspace.publish();
        } else {
            workspace.unpublish();
        }

        // Publishing changes who can read this workspace's documentation, so it is
        // among the most consequential things anyone can do to it.
        auditTrail.record(userId, AuditEntry
                .of(request.published()
                                ? AuditAction.WORKSPACE_PUBLISHED
                                : AuditAction.WORKSPACE_UNPUBLISHED,
                        AuditTargetType.WORKSPACE)
                .target(workspace.getId(), workspace.getName())
                .inWorkspace(workspaceId)
                .with("publicPages", documentDirectory.countByVisibility(workspaceId).publicPages()));

        return respond(workspace);
    }

    private PublicationResponse respond(Workspace workspace) {
        DocumentDirectory.VisibilityCounts counts =
                documentDirectory.countByVisibility(workspace.getId());
        String ownerHandle = userDirectory.findById(workspace.getOwnerUserId())
                .map(owner -> owner.handle())
                .orElse("unknown");
        return PublicationResponse.of(
                workspace, ownerHandle, counts.publicPages(), counts.internalPages());
    }

    private Workspace loadWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }
}
