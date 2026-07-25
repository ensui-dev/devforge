package com.devforge.task.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerIntegrationTest extends AbstractIntegrationTest {

    private TestUser user;
    private UUID workspaceId;
    private UUID boardId;
    private UUID backlogId;
    private UUID inProgressId;

    @BeforeEach
    void createBoardFixture() throws Exception {
        user = registerUser("owner@example.com", "Owner");
        workspaceId = createWorkspace(user, "Platform", "platform");

        JsonNode board = createBoard(user, workspaceId, "Delivery");
        boardId = UUID.fromString(board.get("id").asText());
        backlogId = UUID.fromString(board.get("columns").get(0).get("id").asText());
        inProgressId = UUID.fromString(board.get("columns").get(1).get("id").asText());
    }

    @Test
    void createsTasksInOrderWithinAColumn() throws Exception {
        createTask("First", backlogId);
        createTask("Second", backlogId);

        mockMvc.perform(authed(get(boardPath(), workspaceId, boardId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].tasks.length()").value(2))
                .andExpect(jsonPath("$.columns[0].tasks[0].title").value("First"))
                .andExpect(jsonPath("$.columns[0].tasks[0].position").value(0))
                .andExpect(jsonPath("$.columns[0].tasks[1].title").value("Second"))
                .andExpect(jsonPath("$.columns[0].tasks[1].position").value(1));
    }

    @Test
    void movesATaskBetweenColumns() throws Exception {
        UUID taskId = createTask("Moving", backlogId);

        mockMvc.perform(authed(patch(taskPath() + "/position", workspaceId, boardId, taskId), user)
                        .content("""
                                {"columnId":"%s","position":0}""".formatted(inProgressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columnId").value(inProgressId.toString()))
                .andExpect(jsonPath("$.position").value(0));

        mockMvc.perform(authed(get(boardPath(), workspaceId, boardId), user))
                .andExpect(jsonPath("$.columns[0].tasks.length()").value(0))
                .andExpect(jsonPath("$.columns[1].tasks.length()").value(1));
    }

    /** Reordering must leave the column contiguous, which the old code could not do. */
    @Test
    void reordersWithinAColumnAndKeepsPositionsContiguous() throws Exception {
        createTask("A", backlogId);
        createTask("B", backlogId);
        UUID third = createTask("C", backlogId);

        mockMvc.perform(authed(patch(taskPath() + "/position", workspaceId, boardId, third), user)
                        .content("""
                                {"columnId":"%s","position":0}""".formatted(backlogId)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get(boardPath(), workspaceId, boardId), user))
                .andExpect(jsonPath("$.columns[0].tasks[0].title").value("C"))
                .andExpect(jsonPath("$.columns[0].tasks[0].position").value(0))
                .andExpect(jsonPath("$.columns[0].tasks[1].title").value("A"))
                .andExpect(jsonPath("$.columns[0].tasks[1].position").value(1))
                .andExpect(jsonPath("$.columns[0].tasks[2].title").value("B"))
                .andExpect(jsonPath("$.columns[0].tasks[2].position").value(2));
    }

    @Test
    void movingAcrossColumnsClosesTheSourceGap() throws Exception {
        createTask("Stays", backlogId);
        UUID moving = createTask("Moves", backlogId);
        createTask("Also stays", backlogId);

        mockMvc.perform(authed(patch(taskPath() + "/position", workspaceId, boardId, moving), user)
                        .content("""
                                {"columnId":"%s","position":0}""".formatted(inProgressId)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get(boardPath(), workspaceId, boardId), user))
                .andExpect(jsonPath("$.columns[0].tasks[0].position").value(0))
                .andExpect(jsonPath("$.columns[0].tasks[1].title").value("Also stays"))
                .andExpect(jsonPath("$.columns[0].tasks[1].position").value(1));
    }

    @Test
    void clampsAMovePastTheEndOfAColumn() throws Exception {
        createTask("A", backlogId);
        UUID moving = createTask("B", backlogId);

        mockMvc.perform(authed(patch(taskPath() + "/position", workspaceId, boardId, moving), user)
                        .content("""
                                {"columnId":"%s","position":999}""".formatted(backlogId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void deletingATaskCompactsTheColumn() throws Exception {
        UUID first = createTask("First", backlogId);
        createTask("Second", backlogId);

        mockMvc.perform(authed(delete(taskPath(), workspaceId, boardId, first), user))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get(boardPath(), workspaceId, boardId), user))
                .andExpect(jsonPath("$.columns[0].tasks.length()").value(1))
                .andExpect(jsonPath("$.columns[0].tasks[0].title").value("Second"))
                .andExpect(jsonPath("$.columns[0].tasks[0].position").value(0));
    }

    @Test
    void editingATaskDoesNotChangeItsPlacement() throws Exception {
        createTask("First", backlogId);
        UUID second = createTask("Second", backlogId);

        mockMvc.perform(authed(put(taskPath(), workspaceId, boardId, second), user)
                        .content("""
                                {"title":"Renamed","description":"Updated","priority":"HIGH"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.columnId").value(backlogId.toString()));
    }

    @Test
    void enforcesAColumnWipLimit() throws Exception {
        JsonNode board = objectMapper.readTree(
                mockMvc.perform(authed(post(
                                "/api/workspaces/{id}/boards/{boardId}/columns", workspaceId, boardId), user)
                                .content("""
                                        {"name":"Capped","wipLimit":1}"""))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString());
        UUID cappedId = UUID.fromString(board.get("columns").get(4).get("id").asText());

        createTask("Occupant", cappedId);

        mockMvc.perform(authed(post(tasksPath(), workspaceId, boardId), user)
                        .content("""
                                {"title":"Overflow","columnId":"%s"}""".formatted(cappedId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("work-in-progress limit")));
    }

    @Test
    void assignsATaskToATeammate() throws Exception {
        TestUser teammate = registerUser("dev@example.com", "Dev");
        mockMvc.perform(authed(post("/api/workspaces/{id}/members", workspaceId), user)
                        .content("""
                                {"email":"dev@example.com","role":"MEMBER"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post(tasksPath(), workspaceId, boardId), user)
                        .content("""
                                {"title":"Assigned","columnId":"%s","assigneeId":"%s"}"""
                                .formatted(backlogId, teammate.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignee.displayName").value("Dev"))
                .andExpect(jsonPath("$.assignee.email").value("dev@example.com"));
    }

    /** Assigning work to someone who cannot open the workspace is meaningless. */
    @Test
    void refusesToAssignToANonMember() throws Exception {
        TestUser outsider = registerUser("outsider@example.com", "Outsider");

        mockMvc.perform(authed(post(tasksPath(), workspaceId, boardId), user)
                        .content("""
                                {"title":"Bad assignment","columnId":"%s","assigneeId":"%s"}"""
                                .formatted(backlogId, outsider.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("not a member")));
    }

    @Test
    void citesDocumentsFromATask() throws Exception {
        UUID documentId = createDocument(user, workspaceId, "Spec", "spec", "body", "API");

        mockMvc.perform(authed(post(tasksPath(), workspaceId, boardId), user)
                        .content("""
                                {"title":"Implement spec","columnId":"%s","linkedDocumentIds":["%s"]}"""
                                .formatted(backlogId, documentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkedDocuments.length()").value(1))
                .andExpect(jsonPath("$.linkedDocuments[0].title").value("Spec"))
                .andExpect(jsonPath("$.linkedDocuments[0].documentType").value("API"));
    }

    @Test
    void linksAndUnlinksADocumentAfterCreation() throws Exception {
        UUID taskId = createTask("Task", backlogId);
        UUID documentId = createDocument(user, workspaceId, "Runbook", "runbook", "body", "RUNBOOK");

        mockMvc.perform(authed(post(taskPath() + "/documents", workspaceId, boardId, taskId), user)
                        .content("""
                                {"documentId":"%s"}""".formatted(documentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkedDocuments.length()").value(1));

        mockMvc.perform(authed(post(taskPath() + "/documents", workspaceId, boardId, taskId), user)
                        .content("""
                                {"documentId":"%s"}""".formatted(documentId)))
                .andExpect(status().isConflict());

        mockMvc.perform(authed(delete(
                        taskPath() + "/documents/{documentId}", workspaceId, boardId, taskId, documentId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedDocuments.length()").value(0));
    }

    @Test
    void refusesToCiteADocumentFromAnotherWorkspace() throws Exception {
        UUID otherWorkspace = createWorkspace(user, "Other", "other");
        UUID foreignDocument = createDocument(user, otherWorkspace, "Foreign", "foreign", "body", "GENERAL");
        UUID taskId = createTask("Task", backlogId);

        mockMvc.perform(authed(post(taskPath() + "/documents", workspaceId, boardId, taskId), user)
                        .content("""
                                {"documentId":"%s"}""".formatted(foreignDocument)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingADocumentRemovesItsTaskCitations() throws Exception {
        UUID documentId = createDocument(user, workspaceId, "Spec", "spec", "body", "API");
        UUID taskId = createTask("Task", backlogId);

        mockMvc.perform(authed(post(taskPath() + "/documents", workspaceId, boardId, taskId), user)
                        .content("""
                                {"documentId":"%s"}""".formatted(documentId)))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(delete("/api/workspaces/{id}/documents/{docId}", workspaceId, documentId), user))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get(boardPath(), workspaceId, boardId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].tasks[0].linkedDocuments.length()").value(0));
    }

    @Test
    void rejectsAMoveIntoAColumnOnAnotherBoard() throws Exception {
        UUID taskId = createTask("Task", backlogId);
        JsonNode otherBoard = createBoard(user, workspaceId, "Other Board");
        UUID foreignColumn = UUID.fromString(otherBoard.get("columns").get(0).get("id").asText());

        mockMvc.perform(authed(patch(taskPath() + "/position", workspaceId, boardId, taskId), user)
                        .content("""
                                {"columnId":"%s","position":0}""".formatted(foreignColumn)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsATaskWithoutATitle() throws Exception {
        mockMvc.perform(authed(post(tasksPath(), workspaceId, boardId), user)
                        .content("""
                                {"title":"","columnId":"%s"}""".formatted(backlogId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    private static String boardPath() {
        return "/api/workspaces/{workspaceId}/boards/{boardId}";
    }

    private static String tasksPath() {
        return boardPath() + "/tasks";
    }

    private static String taskPath() {
        return tasksPath() + "/{taskId}";
    }

    private UUID createTask(String title, UUID columnId) throws Exception {
        return UUID.fromString(postForCreated(
                user,
                tasksPath(),
                new TaskPayload(title, columnId.toString()),
                workspaceId, boardId)
                .get("id")
                .asText());
    }

    private record TaskPayload(String title, String columnId) {
    }
}
