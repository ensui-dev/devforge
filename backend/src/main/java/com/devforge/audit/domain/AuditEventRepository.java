package com.devforge.audit.domain;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByWorkspaceIdOrderByOccurredAtDesc(UUID workspaceId, Pageable pageable);

    Page<AuditEvent> findByWorkspaceIdAndActionOrderByOccurredAtDesc(
            UUID workspaceId, AuditAction action, Pageable pageable);

    /** The whole instance, for an operator. Includes events with no workspace. */
    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Page<AuditEvent> findByActionOrderByOccurredAtDesc(AuditAction action, Pageable pageable);

    /** Everything that ever happened to one thing — a document's own history. */
    Page<AuditEvent> findByTargetTypeAndTargetIdOrderByOccurredAtDesc(
            AuditTargetType targetType, UUID targetId, Pageable pageable);
}
