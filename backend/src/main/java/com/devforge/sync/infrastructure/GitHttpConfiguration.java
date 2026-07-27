package com.devforge.sync.infrastructure;

import com.devforge.identity.contract.GitCredentials;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Serves git over HTTP at {@code /git/{handle}/{workspace}.git}.
 *
 * <p>JGit's {@link GitServlet} implements the smart-HTTP protocol — advertising
 * refs, negotiating packs, receiving them — so what remains is deciding who may do
 * what, which is the part that belongs to DevForge.
 *
 * <p>Registered as a servlet rather than a controller because the protocol is not
 * REST: it streams packfiles both ways and uses content types and chunking that a
 * message-converter pipeline would try to be helpful about.
 */
@Configuration
@ConditionalOnProperty(name = "devforge.git.enabled", havingValue = "true", matchIfMissing = true)
public class GitHttpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GitHttpConfiguration.class);

    /** Set by {@link GitAuthenticationFilter} once Basic credentials are verified. */
    static final String USER_ATTRIBUTE = "devforge.git.userId";

    /** The same caller, with the name and address a commit or reflog entry needs. */
    static final String IDENTITY_ATTRIBUTE = "devforge.git.identity";

    private final GitRepositoryStore store;
    private final WorkspaceAccess workspaceAccess;
    private final GitCredentials gitCredentials;
    private final GitPushImporter pushImporter;

    public GitHttpConfiguration(
            GitRepositoryStore store,
            WorkspaceAccess workspaceAccess,
            GitCredentials gitCredentials,
            GitPushImporter pushImporter
    ) {
        this.store = store;
        this.workspaceAccess = workspaceAccess;
        this.gitCredentials = gitCredentials;
        this.pushImporter = pushImporter;
    }

    @Bean
    ServletRegistrationBean<GitServlet> gitServlet() {
        GitServlet servlet = new GitServlet();
        servlet.setRepositoryResolver(repositoryResolver());

        // Cloning needs VIEWER, pushing needs MEMBER: the same bar as reading and
        // writing a document through the API, since that is exactly what each does.
        servlet.setUploadPackFactory(uploadPackFactory());
        servlet.setReceivePackFactory(receivePackFactory());

        ServletRegistrationBean<GitServlet> registration =
                new ServletRegistrationBean<>(servlet, "/git/*");
        registration.setName("gitServlet");
        return registration;
    }

    @Bean
    GitAuthenticationFilter gitAuthenticationFilter() {
        return new GitAuthenticationFilter(gitCredentials);
    }

    /**
     * Maps {@code {handle}/{workspace}.git} to the workspace's bare repository.
     *
     * <p>Answers {@link ServiceNotEnabledException} — which git reports as "not
     * found" — for a workspace the caller cannot see, rather than distinguishing
     * "no such workspace" from "not yours". The API makes the same choice, for the
     * same reason.
     */
    private RepositoryResolver<HttpServletRequest> repositoryResolver() {
        return (request, name) -> {
            UUID userId = (UUID) request.getAttribute(USER_ATTRIBUTE);
            if (userId == null) {
                throw new ServiceNotAuthorizedException();
            }

            // Resolving and authorising are one step, so there is no moment at which
            // a workspace the caller cannot see has been identified.
            Optional<UUID> workspaceId = resolveWorkspace(name, userId, WorkspaceRole.VIEWER);
            if (workspaceId.isEmpty()) {
                throw new org.eclipse.jgit.errors.RepositoryNotFoundException(name);
            }

            try {
                return store.open(workspaceId.get());
            } catch (IOException e) {
                throw new ServiceNotEnabledException(e.getMessage(), e);
            }
        };
    }

    private DefaultUploadPackFactory uploadPackFactory() {
        return new DefaultUploadPackFactory() {
            @Override
            public UploadPack create(HttpServletRequest request, Repository repository)
                    throws ServiceNotEnabledException, ServiceNotAuthorizedException {
                // Reaching here means the resolver already allowed reading.
                return super.create(request, repository);
            }
        };
    }

    /**
     * Pushing needs {@code MEMBER}, and every accepted push is imported.
     *
     * <p>The import runs from a receive hook rather than after the response, so a
     * push that DevForge cannot apply can be rejected while git is still listening —
     * which is the only moment the person pushing will ever see the reason.
     */
    private DefaultReceivePackFactory receivePackFactory() {
        return new DefaultReceivePackFactory() {
            @Override
            public ReceivePack create(HttpServletRequest request, Repository repository)
                    throws ServiceNotEnabledException, ServiceNotAuthorizedException {
                UUID userId = (UUID) request.getAttribute(USER_ATTRIBUTE);
                UUID workspaceId = workspaceIdOf(repository);

                if (userId == null || workspaceId == null || !canWrite(workspaceId, userId)) {
                    throw new ServiceNotAuthorizedException();
                }

                ReceivePack receivePack = super.create(request, repository);
                receivePack.setAllowNonFastForwards(true);
                pushImporter.attachTo(receivePack, workspaceId, userId);
                return receivePack;
            }
        };
    }

    /** The workspace a repository belongs to, taken back out of its directory name. */
    private UUID workspaceIdOf(Repository repository) {
        String directory = repository.getDirectory().getName();
        String id = directory.endsWith(".git")
                ? directory.substring(0, directory.length() - ".git".length())
                : directory;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.warn("Git repository directory {} is not named after a workspace", directory);
            return null;
        }
    }

    /** {@code ada/handbook.git}, as it appears after {@code /git/}. */
    private Optional<UUID> resolveWorkspace(String name, UUID userId, WorkspaceRole required) {
        String path = name.endsWith(".git")
                ? name.substring(0, name.length() - ".git".length())
                : name;
        // Leading slashes vary with how the servlet container hands the path over.
        path = path.replaceAll("^/+", "");

        String[] parts = path.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }

        return workspaceAccess.findForCaller(parts[0], parts[1], userId, required)
                .map(workspace -> workspace.id());
    }

    private boolean canWrite(UUID workspaceId, UUID userId) {
        try {
            workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
            return true;
        } catch (RuntimeException denied) {
            return false;
        }
    }
}
