package com.devforge.task.api;

import com.devforge.support.AbstractIntegrationTest;
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

class BoardControllerIntegrationTest extends AbstractIntegrationTest {

    /**
     * Reading a board back is the exact operation that previously failed with
     * {@code MultipleBagFetchException}, so this is the regression test for it.
     */
    @Test
    void createsABoardWithDefaultColumnsAndReadsItBack() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");

        JsonNode board = createBoard(user, workspaceId, "Delivery Board");
        UUID boardId = UUID.fromString(board.get("id").asText());

        mockMvc.perform(authed(get("/api/workspaces/{id}/boards/{boardId}", workspaceId, boardId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Delivery Board"))
                .andExpect(jsonPath("$.columns.length()").value(4))
                .andExpect(jsonPath("$.columns[0].name").value("Backlog"))
                .andExpect(jsonPath("$.columns[0].position").value(0))
                .andExpect(jsonPath("$.columns[3].name").value("Done"))
                .andExpect(jsonPath("$.columns[3].position").value(3));
    }

    @Test
    void listsBoardsWithCounts() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        JsonNode board = createBoard(user, workspaceId, "Delivery Board");
        UUID columnId = firstColumnId(board);
        createTask(user, workspaceId, UUID.fromString(board.get("id").asText()), columnId, "Task");

        mockMvc.perform(authed(get("/api/workspaces/{id}/boards", workspaceId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].columnCount").value(4))
                .andExpect(jsonPath("$[0].taskCount").value(1));
    }

    @Test
    void renamesABoard() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID boardId = UUID.fromString(createBoard(user, workspaceId, "Old").get("id").asText());

        mockMvc.perform(authed(put("/api/workspaces/{id}/boards/{boardId}", workspaceId, boardId), user)
                        .content("""
                                {"name":"New Name"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void addsAColumnAtTheEnd() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID boardId = UUID.fromString(createBoard(user, workspaceId, "Delivery").get("id").asText());

        mockMvc.perform(authed(post("/api/workspaces/{id}/boards/{boardId}/columns", workspaceId, boardId), user)
                        .content("""
                                {"name":"Blocked","wipLimit":2}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.columns.length()").value(5))
                .andExpect(jsonPath("$.columns[4].name").value("Blocked"))
                .andExpect(jsonPath("$.columns[4].wipLimit").value(2));
    }

    /** Reordering must renumber siblings and return them in the new order. */
    @Test
    void reordersColumns() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        JsonNode board = createBoard(user, workspaceId, "Delivery");
        UUID boardId = UUID.fromString(board.get("id").asText());
        UUID doneId = UUID.fromString(board.get("columns").get(3).get("id").asText());

        mockMvc.perform(authed(patch(
                        "/api/workspaces/{id}/boards/{boardId}/columns/{columnId}/position",
                        workspaceId, boardId, doneId), user)
                        .content("""
                                {"position":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].name").value("Done"))
                .andExpect(jsonPath("$.columns[0].position").value(0))
                .andExpect(jsonPath("$.columns[1].name").value("Backlog"))
                .andExpect(jsonPath("$.columns[1].position").value(1))
                .andExpect(jsonPath("$.columns[3].name").value("Review"))
                .andExpect(jsonPath("$.columns[3].position").value(3));

        // The new order survives a reload, so it was persisted, not just returned.
        mockMvc.perform(authed(get("/api/workspaces/{id}/boards/{boardId}", workspaceId, boardId), user))
                .andExpect(jsonPath("$.columns[0].name").value("Done"));
    }

    @Test
    void renamesAColumnAndSetsAWipLimit() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        JsonNode board = createBoard(user, workspaceId, "Delivery");
        UUID boardId = UUID.fromString(board.get("id").asText());
        UUID columnId = firstColumnId(board);

        mockMvc.perform(authed(put(
                        "/api/workspaces/{id}/boards/{boardId}/columns/{columnId}",
                        workspaceId, boardId, columnId), user)
                        .content("""
                                {"name":"To Do","wipLimit":5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].name").value("To Do"))
                .andExpect(jsonPath("$.columns[0].wipLimit").value(5));
    }

    @Test
    void deletesAColumnAndClosesThePositionGap() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        JsonNode board = createBoard(user, workspaceId, "Delivery");
        UUID boardId = UUID.fromString(board.get("id").asText());
        UUID inProgressId = UUID.fromString(board.get("columns").get(1).get("id").asText());

        mockMvc.perform(authed(delete(
                        "/api/workspaces/{id}/boards/{boardId}/columns/{columnId}",
                        workspaceId, boardId, inProgressId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].position").value(0))
                .andExpect(jsonPath("$.columns[1].name").value("Review"))
                .andExpect(jsonPath("$.columns[1].position").value(1))
                .andExpect(jsonPath("$.columns[2].position").value(2));
    }

    @Test
    void rejectsAnInvalidWipLimit() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID boardId = UUID.fromString(createBoard(user, workspaceId, "Delivery").get("id").asText());

        mockMvc.perform(authed(post("/api/workspaces/{id}/boards/{boardId}/columns", workspaceId, boardId), user)
                        .content("""
                                {"name":"Bad","wipLimit":0}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesABoardWithItsTasks() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        JsonNode board = createBoard(user, workspaceId, "Delivery");
        UUID boardId = UUID.fromString(board.get("id").asText());
        createTask(user, workspaceId, boardId, firstColumnId(board), "Doomed");

        mockMvc.perform(authed(delete("/api/workspaces/{id}/boards/{boardId}", workspaceId, boardId), user))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/workspaces/{id}/boards/{boardId}", workspaceId, boardId), user))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesBoardsFromNonMembers() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser stranger = registerUser("stranger@example.com", "Stranger");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        UUID boardId = UUID.fromString(createBoard(owner, workspaceId, "Delivery").get("id").asText());

        mockMvc.perform(authed(get("/api/workspaces/{id}/boards/{boardId}", workspaceId, boardId), stranger))
                .andExpect(status().isNotFound());
    }

    private UUID firstColumnId(JsonNode board) {
        return UUID.fromString(board.get("columns").get(0).get("id").asText());
    }

    private void createTask(TestUser user, UUID workspaceId, UUID boardId, UUID columnId, String title)
            throws Exception {
        mockMvc.perform(authed(post("/api/workspaces/{id}/boards/{boardId}/tasks", workspaceId, boardId), user)
                        .content("""
                                {"title":"%s","columnId":"%s"}""".formatted(title, columnId)))
                .andExpect(status().isCreated());
    }
}
