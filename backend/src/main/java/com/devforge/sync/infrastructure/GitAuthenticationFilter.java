package com.devforge.sync.infrastructure;

import com.devforge.identity.contract.GitCredentials;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * HTTP Basic authentication for git.
 *
 * <p>Git speaks Basic and nothing else, so this cannot go through the bearer-token
 * resource server the rest of the API uses. Kept as a plain filter rather than a
 * second Spring Security chain because what it does is genuinely simple — read a
 * header, resolve a token, attach a user — and expressing that through an
 * authentication provider, a user-details service and a matcher would bury it.
 *
 * <p>The username is ignored. Git always sends one, people type whatever they like,
 * and the token already identifies its owner — rejecting a mismatch would produce a
 * failure whose cause nobody could guess.
 */
@Order(1)
public class GitAuthenticationFilter extends OncePerRequestFilter {

    private static final String REALM = "DevForge";

    private final GitCredentials credentials;

    public GitAuthenticationFilter(GitCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/git/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        Optional<GitCredentials.GitIdentity> identity =
                presentedSecret(request).flatMap(credentials::authenticate);

        if (identity.isEmpty()) {
            // The status and challenge are written directly rather than through
            // sendError, which hands the request to the container's error dispatch
            // and loses the WWW-Authenticate header on the way. Without the
            // challenge a git client reports "authentication not supported" and
            // never offers the credentials it was holding.
            //
            // Anonymous access is not offered: documentation is published through
            // /docs, and a repository is not a second public surface.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\"");
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("A DevForge git access token is required.");
            return;
        }

        GitCredentials.GitIdentity who = identity.get();
        request.setAttribute(GitHttpConfiguration.USER_ATTRIBUTE, who.userId());
        request.setAttribute(GitHttpConfiguration.IDENTITY_ATTRIBUTE, who);

        // JGit's DefaultReceivePackFactory refuses a push when getRemoteUser() is
        // null — it reads that as anonymous, whatever else the request carries. A
        // request attribute is invisible to it, so the request is wrapped instead.
        // JGit also uses the remote user for the reflog entry recording who moved
        // a ref, which is worth getting right rather than working around.
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteUser() {
                return who.handle();
            }
        }, response);
    }

    /** The password half of a Basic header, which is where a git token goes. */
    private static Optional<String> presentedSecret(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }

        try {
            String decoded = new String(
                    Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            // No colon means no password was sent at all.
            return colon < 0 ? Optional.empty() : Optional.of(decoded.substring(colon + 1));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
