package com.devforge.identity.api;

import com.devforge.identity.application.CreateGitAccessTokenRequest;
import com.devforge.identity.application.GitAccessTokenResponse;
import com.devforge.identity.application.GitCredentialService;
import com.devforge.identity.domain.GitAccessToken;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in account's own git credentials.
 *
 * <p>Scoped to the caller throughout — there is no path here that names a user, so
 * there is no way to list or revoke somebody else's. An instance administrator
 * cannot read these either: a token is a credential, and the only thing anyone
 * needs to do to someone else's is to be able to take an account away.
 */
@RestController
@RequestMapping("/api/me/git-tokens")
@Tag(name = "Git access tokens")
public class GitAccessTokenController {

    private final GitCredentialService credentials;

    public GitAccessTokenController(GitCredentialService credentials) {
        this.credentials = credentials;
    }

    @GetMapping
    @Operation(summary = "The git access tokens on this account")
    public List<GitAccessTokenResponse> list(@CurrentUser UUID userId) {
        return credentials.listFor(userId).stream().map(GitAccessTokenResponse::from).toList();
    }

    /**
     * @return the token, including its secret — the only time it is ever readable
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a git access token, returning the secret once")
    public IssuedGitAccessToken create(
            @Valid @RequestBody CreateGitAccessTokenRequest request,
            @CurrentUser UUID userId
    ) {
        Instant expiresAt = request.expiresInDays() == null || request.expiresInDays() <= 0
                ? null
                : Instant.now().plus(request.expiresInDays(), ChronoUnit.DAYS);

        GitAccessToken.Issued issued = credentials.issue(userId, request.name(), expiresAt);
        return new IssuedGitAccessToken(
                GitAccessTokenResponse.from(issued.token()), issued.secret());
    }

    @DeleteMapping("/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a git access token")
    public void revoke(@PathVariable UUID tokenId, @CurrentUser UUID userId) {
        if (!credentials.revoke(tokenId, userId)) {
            // Someone else's token is reported as absent rather than forbidden: the
            // difference would confirm that it exists.
            throw new ResourceNotFoundException("Git access token", tokenId);
        }
    }

    /**
     * @param secret shown once, because only its digest was kept — the interface
     *               says so rather than offering to reveal it later
     */
    public record IssuedGitAccessToken(GitAccessTokenResponse token, String secret) {
    }
}
