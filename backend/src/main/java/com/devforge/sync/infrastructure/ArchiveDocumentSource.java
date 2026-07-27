package com.devforge.sync.infrastructure;

import com.devforge.sync.application.DocumentSource;
import com.devforge.sync.application.SourceFile;
import com.devforge.sync.application.SourceSnapshot;
import com.devforge.sync.application.SourceUnavailableException;
import com.devforge.sync.domain.SyncConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Fetches documentation by downloading a repository archive.
 *
 * <p>Every mainstream git host serves a zip of a ref at a predictable path — GitHub,
 * GitLab, Gitea, and Forgejo all do. Downloading that and reading it with
 * {@code java.util.zip} needs no git client, no packfile handling, no credential
 * helper, and no new dependency: it is one HTTPS GET and the JDK.
 *
 * <p>The trade-off is honest and worth naming. This has no notion of history — it
 * applies the state of a ref, and cannot tell which files a particular commit
 * touched. For "make the workspace match the repository" that is sufficient, and it
 * is why the planner works by comparing whole sets rather than by replaying a diff.
 *
 * <p>Guarded against a hostile or careless archive: entries are size-capped, the
 * total is capped, and paths that try to escape their directory are refused.
 */
@Component
public class ArchiveDocumentSource implements DocumentSource {

    private static final Logger log = LoggerFactory.getLogger(ArchiveDocumentSource.class);

    /** A single documentation file past this is not documentation. */
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    /** Total uncompressed budget, so a zip bomb cannot exhaust the heap. */
    private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final int MAX_FILES = 5_000;

    private final HttpClient http;

    public ArchiveDocumentSource() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    /** For tests, which point it at a local server. */
    ArchiveDocumentSource(HttpClient http) {
        this.http = http;
    }

    @Override
    public SourceSnapshot fetch(SyncConfiguration configuration, String accessToken) {
        URI archive = archiveUri(configuration);

        HttpRequest.Builder request = HttpRequest.newBuilder(archive)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/zip")
                .header("User-Agent", "DevForge")
                .GET();

        if (accessToken != null && !accessToken.isBlank()) {
            // Bearer works for GitHub, Gitea, and Forgejo. GitLab wants
            // PRIVATE-TOKEN, so both are sent; hosts ignore headers they do not know.
            request.header("Authorization", "Bearer " + accessToken);
            request.header("PRIVATE-TOKEN", accessToken);
        }

        HttpResponse<byte[]> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new SourceUnavailableException(
                    "Could not reach %s (%s)".formatted(archive.getHost(), e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("Interrupted while fetching the repository", e);
        }

        if (response.statusCode() == 404) {
            throw new SourceUnavailableException(
                    ("Nothing found at that repository and branch. Check the URL and that "
                     + "branch '%s' exists.").formatted(configuration.getBranch()));
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new SourceUnavailableException(
                    accessToken == null
                            ? "That repository is private. Add an access token with read access."
                            : "The access token was refused. Check that it is valid and can read "
                              + "this repository.");
        }
        if (response.statusCode() >= 400) {
            throw new SourceUnavailableException(
                    "The git host returned %d when asked for the archive."
                            .formatted(response.statusCode()));
        }

        return new SourceSnapshot(configuration.getBranch(), read(response.body()));
    }

    /**
     * Whether the bytes are a zip at all.
     *
     * <p>{@code ZipInputStream} does not object to being handed something else — it
     * simply finds no entries. That would surface as "no documentation found", which
     * points the operator at their document path when the real problem is that the
     * host returned an HTML error page or a login redirect.
     *
     * <p>Every zip begins {@code PK}: {@code PK\x03\x04} for one with entries,
     * {@code PK\x05\x06} for an empty one.
     */
    private static boolean looksLikeZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    /**
     * Where the archive lives.
     *
     * <p>{@code /archive/{ref}.zip} is what Gitea, Forgejo, and GitHub all accept.
     * A trailing {@code .git} is stripped because that is how a clone URL is usually
     * copied, and it is not part of the web path.
     */
    private static URI archiveUri(SyncConfiguration configuration) {
        String base = configuration.getRepositoryUrl().trim()
                .replaceAll("/+$", "")
                .replaceAll("\\.git$", "");

        if (!base.startsWith("https://") && !base.startsWith("http://")) {
            throw new SourceUnavailableException(
                    "The repository URL must start with https://");
        }

        try {
            // The ref goes in a path segment, so anything that could break out of it
            // is rejected rather than escaped — a branch name is not a place for
            // path traversal.
            String ref = configuration.getBranch();
            if (ref.contains("..") || ref.startsWith("/")) {
                throw new SourceUnavailableException("That branch name is not usable in a URL.");
            }
            return new URI(base + "/archive/" + ref.replace(" ", "%20") + ".zip");
        } catch (URISyntaxException e) {
            throw new SourceUnavailableException("The repository URL is not a valid address.", e);
        }
    }

    /**
     * Reads markdown out of the archive.
     *
     * <p>Hosts wrap the tree in a single top-level directory named after the repo and
     * ref, which is an implementation detail of the archive rather than part of the
     * repository, so it is stripped — otherwise every configured document path would
     * have to include a directory nobody chose.
     */
    private static List<SourceFile> read(byte[] archive) {
        if (!looksLikeZip(archive)) {
            throw new SourceUnavailableException(
                    "The git host did not return a zip archive. It may have replied with an error "
                    + "page or a login redirect — check the repository URL.");
        }

        List<SourceFile> files = new ArrayList<>();
        long total = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (files.size() >= MAX_FILES) {
                    log.warn("Archive contains more than {} files; the rest were ignored", MAX_FILES);
                    break;
                }

                String path = stripTopLevel(entry.getName());
                if (path == null || !isMarkdown(path)) {
                    continue;
                }

                byte[] bytes = zip.readNBytes((int) MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES) {
                    log.warn("Skipping {}: larger than {} bytes", path, MAX_FILE_BYTES);
                    continue;
                }

                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new SourceUnavailableException(
                            "The repository's documentation is larger than this instance will read "
                            + "in one sync.");
                }

                files.add(new SourceFile(path, new String(bytes, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new SourceUnavailableException(
                    "The downloaded archive could not be read. The git host may have returned "
                    + "something other than a zip.", e);
        }

        return files;
    }

    private static boolean isMarkdown(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    /**
     * Drops the archive's wrapper directory, and refuses anything trying to escape.
     *
     * @return the repository-relative path, or null if the entry should be ignored
     */
    static String stripTopLevel(String name) {
        // Zip slip: an entry named ../../etc/passwd. Nothing is written to disk here,
        // but a path like that would also defeat the document-path check, so it goes.
        if (name.contains("..") || name.startsWith("/") || name.contains("\\")) {
            return null;
        }
        int slash = name.indexOf('/');
        if (slash < 0) {
            // A file at the archive root, outside the wrapper directory. Not part of
            // the repository tree as the host lays it out.
            return null;
        }
        String path = name.substring(slash + 1);
        return path.isEmpty() ? null : path;
    }
}
