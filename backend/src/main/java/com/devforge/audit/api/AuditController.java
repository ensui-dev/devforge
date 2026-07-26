package com.devforge.audit.api;

import com.devforge.audit.application.AuditEventResponse;
import com.devforge.audit.application.AuditQueryService;
import com.devforge.audit.contract.AuditAction;
import com.devforge.shared.application.PageResponse;
import com.devforge.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Activity")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/api/workspaces/{workspaceId}/activity")
    @Operation(summary = "What has changed in this workspace, most recent first")
    public PageResponse<AuditEventResponse> workspaceActivity(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @CurrentUser UUID userId
    ) {
        return auditQueryService.forWorkspace(
                workspaceId, userId, action, PageRequest.of(page, size));
    }

    @GetMapping("/api/instance/activity")
    @Operation(summary = "Everything that has happened on this instance (instance admin)")
    public PageResponse<AuditEventResponse> instanceActivity(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @CurrentUser UUID userId
    ) {
        return auditQueryService.forInstance(userId, action, PageRequest.of(page, size));
    }
}
