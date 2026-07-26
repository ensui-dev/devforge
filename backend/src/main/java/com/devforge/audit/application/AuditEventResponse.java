package com.devforge.audit.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.domain.AuditEvent;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One entry as a reader sees it.
 *
 * @param actorLabel who did it, as they were called at the time. {@code null}
 *                   means the event had no signed-in actor, such as first-run
 *                   setup.
 * @param detail     the action's specifics, shape varying by action
 */
public record AuditEventResponse(
        UUID id,
        Instant occurredAt,
        UUID actorId,
        String actorLabel,
        AuditAction action,
        AuditTargetType targetType,
        UUID targetId,
        String targetLabel,
        UUID workspaceId,
        Map<String, Object> detail
) {

    @SuppressWarnings("unchecked")
    public static AuditEventResponse from(AuditEvent event, JsonMapper jsonMapper) {
        Map<String, Object> detail = Map.of();
        if (event.getDetail() != null) {
            try {
                detail = jsonMapper.readValue(event.getDetail(), Map.class);
            } catch (RuntimeException e) {
                // A row that cannot be parsed still tells you who did what and
                // when, which is most of the value. Losing the whole page over
                // one malformed payload would not be.
                detail = Map.of("unparseable", event.getDetail());
            }
        }

        return new AuditEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getActorId(),
                event.getActorLabel(),
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                event.getTargetLabel(),
                event.getWorkspaceId(),
                detail
        );
    }
}
