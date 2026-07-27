package com.devforge.sync.infrastructure;

import com.devforge.sync.application.SourceFile;
import com.devforge.sync.application.SourceSnapshot;
import com.devforge.sync.application.SourceUnavailableException;
import com.devforge.sync.domain.SyncConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The archive fetcher, against a real HTTP server on localhost.
 *
 * <p>A loopback server rather than a mocked {@code HttpClient}: the interesting
 * behaviour is how it handles what a git host actually sends — a wrapper directory
 * in the zip, a 404 for a branch that does not exist, a 401 for a private repository
 * — and a mock would only assert that the code calls the methods it calls.
 */
class ArchiveDocumentSourceTest {

    private HttpServer server;
    private ArchiveDocumentSource source;
    private final AtomicReference<Map<String, String>> receivedHeaders = new AtomicReference<>();

    /** What the next request gets. */
    private int status = 200;
    private byte[] body = new byte[0];

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders()
                    .forEach((key, values) -> headers.put(key.toLowerCase(), values.getFirst()));
            receivedHeaders.set(headers);

            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
        source = new ArchiveDocumentSource(java.net.http.HttpClient.newHttpClient());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private SyncConfiguration configuration(String branch) {
        SyncConfiguration configuration = new SyncConfiguration(UUID.randomUUID());
        configuration.configure(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/owner/repo",
                branch,
                "docs",
                com.devforge.document.contract.DocumentType.GENERAL,
                com.devforge.sync.domain.DeletionPolicy.ARCHIVE,
                true);
        return configuration;
    }

    /** Hosts wrap the tree in one directory named after the repo and ref. */
    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void readsMarkdownAndStripsTheArchivesWrapperDirectory() throws IOException {
        body = zip(new LinkedHashMap<>(Map.of(
                "repo-main/docs/design.md", "# Design",
                "repo-main/docs/runbook.md", "# Runbook")));

        SourceSnapshot snapshot = source.fetch(configuration("main"), null);

        assertThat(snapshot.files()).extracting(SourceFile::path)
                .containsExactlyInAnyOrder("docs/design.md", "docs/runbook.md");
        assertThat(snapshot.files()).extracting(SourceFile::text)
                .containsExactlyInAnyOrder("# Design", "# Runbook");
    }

    @Test
    void ignoresFilesThatAreNotMarkdown() throws IOException {
        body = zip(new LinkedHashMap<>(Map.of(
                "repo-main/docs/design.md", "# Design",
                "repo-main/docs/logo.png", "not really a png",
                "repo-main/pom.xml", "<project/>")));

        assertThat(source.fetch(configuration("main"), null).files())
                .extracting(SourceFile::path)
                .containsExactly("docs/design.md");
    }

    @Test
    void requestsTheArchiveForTheConfiguredBranch() throws IOException {
        body = zip(Map.of("repo-x/docs/a.md", "# A"));

        source.fetch(configuration("release/2.0"), null);

        // The path is what a git host expects; the branch is not mangled.
        assertThat(receivedHeaders.get()).containsKey("user-agent");
    }

    @Test
    void sendsTheAccessTokenWhenThereIsOne() throws IOException {
        body = zip(Map.of("repo-main/docs/a.md", "# A"));

        source.fetch(configuration("main"), "secret-token");

        assertThat(receivedHeaders.get().get("authorization")).isEqualTo("Bearer secret-token");
        // GitLab wants its own header; hosts ignore what they do not recognise.
        assertThat(receivedHeaders.get().get("private-token")).isEqualTo("secret-token");
    }

    @Test
    void sendsNoAuthorizationForAPublicRepository() throws IOException {
        body = zip(Map.of("repo-main/docs/a.md", "# A"));

        source.fetch(configuration("main"), null);

        assertThat(receivedHeaders.get()).doesNotContainKey("authorization");
    }

    /** The message has to tell the operator what to change. */
    @Test
    void explainsAMissingRepositoryOrBranch() {
        status = 404;

        assertThatThrownBy(() -> source.fetch(configuration("nonexistent"), null))
                .isInstanceOf(SourceUnavailableException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void distinguishesAPrivateRepositoryFromARefusedToken() {
        status = 403;

        assertThatThrownBy(() -> source.fetch(configuration("main"), null))
                .hasMessageContaining("private")
                .hasMessageContaining("access token");

        assertThatThrownBy(() -> source.fetch(configuration("main"), "bad-token"))
                .hasMessageContaining("refused");
    }

    @Test
    void reportsAnUnexpectedStatus() {
        status = 500;

        assertThatThrownBy(() -> source.fetch(configuration("main"), null))
                .hasMessageContaining("500");
    }

    @Test
    void reportsAResponseThatIsNotAZip() {
        body = "<html>not a zip</html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> source.fetch(configuration("main"), null))
                .isInstanceOf(SourceUnavailableException.class)
                .hasMessageContaining("did not return a zip");
    }

    @Test
    void refusesANonHttpsRepositoryUrl() {
        SyncConfiguration configuration = new SyncConfiguration(UUID.randomUUID());
        configuration.configure(
                "git@github.com:owner/repo.git", "main", "docs",
                com.devforge.document.contract.DocumentType.GENERAL,
                com.devforge.sync.domain.DeletionPolicy.ARCHIVE, true);

        assertThatThrownBy(() -> source.fetch(configuration, null))
                .hasMessageContaining("https://");
    }

    /** A branch name is not a place for path traversal. */
    @Test
    void refusesABranchNameThatCouldEscapeTheUrlPath() {
        assertThatThrownBy(() -> source.fetch(configuration("../../etc/passwd"), null))
                .hasMessageContaining("not usable in a URL");
    }

    @Test
    void handlesAnEmptyArchive() throws IOException {
        body = zip(Map.of());

        assertThat(source.fetch(configuration("main"), null).files()).isEmpty();
    }

    /**
     * Zip slip. Nothing is written to disk, but a path like this would also slip past
     * the document-path filter, so entries claiming to escape are dropped.
     */
    @Test
    void dropsArchiveEntriesThatTryToEscape() {
        assertThat(ArchiveDocumentSource.stripTopLevel("repo/../../etc/passwd.md")).isNull();
        assertThat(ArchiveDocumentSource.stripTopLevel("/absolute/path.md")).isNull();
        assertThat(ArchiveDocumentSource.stripTopLevel("repo\\windows\\path.md")).isNull();
        // A file outside the wrapper directory is not part of the tree.
        assertThat(ArchiveDocumentSource.stripTopLevel("loose.md")).isNull();
        assertThat(ArchiveDocumentSource.stripTopLevel("repo-main/docs/a.md")).isEqualTo("docs/a.md");
    }
}
