package com.devforge.audit.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.domain.AuditEvent;
import com.devforge.audit.domain.AuditEventRepository;
import com.devforge.identity.contract.UserDirectory;
import com.devforge.shared.application.PageResponse;
import com.devforge.shared.exception.PermissionDeniedException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Reads the audit log.
 *
 * <p>Separate from {@link AuditRecorder} because reading needs permissions and
 * writing must not. If one class did both, every module that records an event
 * would transitively depend on {@code WorkspaceAccess} — including the workspace
 * module itself.
 *
 * <p>A workspace's history is visible to its members at {@code VIEWER}, the same
 * bar as reading its documents: it reveals nothing they cannot already see, and
 * knowing who changed what is part of reading a workspace honestly. Non-members
 * get the same 404 they get for the workspace itself.
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditEventRepository repository;
    private final WorkspaceAccess workspaceAccess;
    private final UserDirectory userDirectory;
    private final JsonMapper jsonMapper;

    public AuditQueryService(
            AuditEventRepository repository,
            WorkspaceAccess workspaceAccess,
            UserDirectory userDirectory,
            JsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.workspaceAccess = workspaceAccess;
        this.userDirectory = userDirectory;
        this.jsonMapper = jsonMapper;
    }

    public PageResponse<AuditEventResponse> forWorkspace(
            UUID workspaceId,
            UUID userId,
            AuditAction action,
            Pageable pageable
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);

        Page<AuditEvent> page = action == null
                ? repository.findByWorkspaceIdOrderByOccurredAtDesc(workspaceId, pageable)
                : repository.findByWorkspaceIdAndActionOrderByOccurredAtDesc(
                        workspaceId, action, pageable);

        return PageResponse.of(page, event -> AuditEventResponse.from(event, jsonMapper));
    }

    /**
     * Everything on the instance, including events that belong to no workspace.
     *
     * <p>Instance administrators only: this spans workspaces the reader may not
     * be a member of.
     */
    public PageResponse<AuditEventResponse> forInstance(
            UUID userId,
            AuditAction action,
            Pageable pageable
    ) {
        if (!userDirectory.isInstanceAdmin(userId)) {
            throw new PermissionDeniedException(
                    "Only an instance administrator can read the instance activity log.");
        }

        Page<AuditEvent> page = action == null
                ? repository.findAllByOrderByOccurredAtDesc(pageable)
                : repository.findByActionOrderByOccurredAtDesc(action, pageable);

        return PageResponse.of(page, event -> AuditEventResponse.from(event, jsonMapper));
    }
}
