package com.devforge.document.api;

import com.devforge.document.domain.DocumentContentRepository;
import com.devforge.document.domain.DocumentRevisionRepository;
import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two storage properties borrowed from git.
 *
 * <p>Git stores snapshots, not deltas — so does this. What git adds on top, and
 * what these tests pin, is that identical content is stored once, and that a
 * commit changing nothing is refused.
 *
 * <p>Both matter here specifically. A restore produces a body byte-identical to
 * one already stored, so it is guaranteed duplication rather than a hypothetical
 * saving; and saving a document twice without editing it would otherwise fill
 * history with entries that say nothing.
 */
class ContentAddressingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DocumentContentRepository contents;

    @Autowired
    private DocumentRevisionRepository revisions;

    @PersistenceContext
    private EntityManager entityManager;

    private String body(String title, String content) {
        return """
                {"title":"%s","slug":"page","content":"%s",
                 "documentType":"GENERAL","internal":false}"""
                .formatted(title, content);
    }

    /** The reason content addressing is worth having at all. */
    @Test
    void restoringStoresNoNewContent() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "First", "page", "the original body", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                        .content(body("Second", "a different body")))
                .andExpect(status().isOk());

        assertThat(contents.countByDocumentId(doc))
                .as("two distinct bodies so far")
                .isEqualTo(2);

        mockMvc.perform(authed(
                        post("/api/workspaces/{w}/documents/{d}/revisions/1/restore", ws, doc), owner))
                .andExpect(status().isOk());

        assertThat(revisions.countByDocumentId(doc))
                .as("history still grew — restoring appends")
                .isEqualTo(3);
        assertThat(contents.countByDocumentId(doc))
                .as("but the restored body already existed, so nothing was stored")
                .isEqualTo(2);
    }

    /** Reverting an edit by hand deduplicates the same way. */
    @Test
    void returningToEarlierContentByHandStoresNothingNew() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "Page", "page", "version one", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                        .content(body("Page", "version two")))
                .andExpect(status().isOk());
        // Same body as revision 1, typed out again rather than restored.
        mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                        .content(body("Page", "version one")))
                .andExpect(status().isOk());

        assertThat(revisions.countByDocumentId(doc)).isEqualTo(3);
        assertThat(contents.countByDocumentId(doc))
                .as("only two distinct bodies were ever written")
                .isEqualTo(2);
    }

    /** Git refuses an empty commit; so does this. */
    @Test
    void savingWithoutChangingAnythingAddsNoRevision() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "Page", "page", "unchanged", "GENERAL");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                            .content(body("Page", "unchanged")))
                    .andExpect(status().isOk());
        }

        assertThat(revisions.countByDocumentId(doc))
                .as("three no-op saves left the single creation revision alone")
                .isEqualTo(1);

        // And the log is not padded with entries that record nothing either.
        mockMvc.perform(authed(get("/api/workspaces/{w}/activity?action=DOCUMENT_UPDATED", ws), owner))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** A metadata-only change is still a change. */
    @Test
    void renamingWithTheSameBodyStillAddsARevision() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "Before", "page", "same body", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                        .content(body("After", "same body")))
                .andExpect(status().isOk());

        assertThat(revisions.countByDocumentId(doc))
                .as("the title moved, so history records it")
                .isEqualTo(2);
        assertThat(contents.countByDocumentId(doc))
                .as("the body did not, so it is stored once")
                .isEqualTo(1);
    }

    /**
     * The application and the migration must hash identically, or content
     * backfilled by SQL would be silently duplicated by the first edit.
     */
    @Test
    void theBackfilledHashMatchesWhatTheApplicationComputes() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "Page", "page", "hash me", "GENERAL");

        assertThat(contents.existsByDocumentIdAndContentHash(
                doc, com.devforge.document.domain.DocumentContent.hash("hash me")))
                .as("SHA-256 hex, agreeing with the migration's expression")
                .isTrue();
    }

    /**
     * The migration's hashing expression, run against content that actually breaks
     * the naive version.
     *
     * <p>This is a regression test for a real defect. The backfill first used
     * {@code content::bytea}, which routes text through {@code bytea_in} and
     * <em>interprets</em> backslash escapes. On a body containing a literal
     * backslash that either fails the migration outright — which is what happened,
     * on the first deployment carrying real documentation — or hashes different
     * bytes than Java does and silently stores a duplicate of content that already
     * existed.
     *
     * <p>The backfill itself cannot be exercised by a test, because it only touches
     * rows that predate the migration and the schema is built before any test data
     * exists. So the expression is asserted directly instead.
     */
    @Test
    @Transactional
    void theMigrationsHashExpressionAgreesWithJavaOnAwkwardContent() {
        String[] awkward = {
                "plain",
                "a backslash \\ here",
                "escape-looking \\n sequence",
                "windows\\path\\to\\file",
                "hex-looking \\x41 sequence",
                "unicode \u00e9\u00e8 and an emoji \ud83d\udd27",
                "",
        };

        for (String content : awkward) {
            String fromSql = (String) entityManager
                    .createNativeQuery(
                            "SELECT encode(sha256(convert_to(CAST(:body AS text), 'UTF8')), 'hex')")
                    .setParameter("body", content)
                    .getSingleResult();

            assertThat(fromSql)
                    .as("migration and application must agree on %s", describe(content))
                    .isEqualTo(com.devforge.document.domain.DocumentContent.hash(content));
        }
    }

    private static String describe(String content) {
        return content.isEmpty() ? "empty content" : "\"" + content + "\"";
    }

    /**
     * Bodies round-trip exactly through the content store.
     *
     * <p>Uses the JSON-serialising helper rather than a hand-escaped literal, so
     * the assertion compares the string that was sent against the string that came
     * back with no escaping arithmetic in between — an earlier version of this test
     * did the escaping by hand and was impossible to reason about.
     */
    @Test
    void revisionBodiesAreReturnedVerbatim() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        String tricky = """
                # Heading

                - item with "quotes" and a backslash \\ and a tab\tthere

                ```java
                var x = "quoted";
                ```""";

        UUID doc = createDocument(owner, ws, "Page", "page", tricky, "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions/1", ws, doc), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(tricky));

        assertThat(contents.countByDocumentId(doc)).isEqualTo(1);
    }
}
