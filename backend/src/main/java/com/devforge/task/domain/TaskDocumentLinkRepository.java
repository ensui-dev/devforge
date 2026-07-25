package com.devforge.task.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskDocumentLinkRepository extends JpaRepository<TaskDocumentLink, UUID> {

    List<TaskDocumentLink> findByTaskId(UUID taskId);

    /** Batch lookup so rendering a whole board costs one query for all links. */
    List<TaskDocumentLink> findByTaskIdIn(Collection<UUID> taskIds);

    Optional<TaskDocumentLink> findByTaskIdAndDocumentId(UUID taskId, UUID documentId);

    boolean existsByTaskIdAndDocumentId(UUID taskId, UUID documentId);
}
