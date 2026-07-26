package com.devforge.identity.application;

import com.devforge.identity.domain.User;
import com.devforge.identity.domain.UserRepository;
import com.devforge.shared.exception.PermissionDeniedException;
import com.devforge.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @Mock
    private AccountProvisioningService accountProvisioning;

    @Mock
    private com.devforge.instance.contract.InstancePolicy instancePolicy;

    @InjectMocks
    private AuthService authService;

    /** Registration is refused unless the instance permits it, so most tests allow it. */
    private void givenRegistrationAllowed() {
        when(instancePolicy.registrationAllowedFor(any())).thenReturn(true);
    }

    @Test
    void registersAUserWhenTheInstanceAllowsIt() {
        givenRegistrationAllowed();
        when(accountProvisioning.create(any(), any(), any(), eq(false)))
                .thenReturn(new User("dev@example.com", "Dev", "dev", "hashed"));
        givenTokenIssued();

        AuthenticationResponse response = authService.register(
                new RegisterRequest("dev@example.com", "Dev", "password123"));

        assertThat(response.accessToken()).isEqualTo("issued-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("dev@example.com");
    }

    @Test
    void normalisesTheEmailBeforeCheckingPolicy() {
        givenRegistrationAllowed();
        when(accountProvisioning.create(any(), any(), any(), eq(false)))
                .thenReturn(new User("dev@example.com", "Dev Name", "dev", "hashed"));
        givenTokenIssued();

        authService.register(new RegisterRequest("  Dev@Example.COM  ", "  Dev Name  ", "password123"));

        // Policy decides on the folded address, not on whatever casing was typed.
        verify(instancePolicy).registrationAllowedFor("dev@example.com");
    }

    /** The operator's policy, not identity's, decides who may join. */
    @Test
    void refusesRegistrationTheInstanceDoesNotPermit() {
        when(instancePolicy.registrationAllowedFor(any())).thenReturn(false);
        when(instancePolicy.registrationRefusalReason())
                .thenReturn("This instance is not accepting new accounts.");

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("dev@example.com", "Dev", "password123")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("not accepting new accounts");

        verify(accountProvisioning, never()).create(any(), any(), any(), anyBoolean());
    }

    @Test
    void logsInWithCorrectCredentials() {
        User user = new User("dev@example.com", "Dev", "dev", "hashed");
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        givenTokenIssued();

        assertThat(authService.login(new LoginRequest("dev@example.com", "password123")).accessToken())
                .isEqualTo("issued-token");
    }

    @Test
    void rejectsAWrongPassword() {
        User user = new User("dev@example.com", "Dev", "dev", "hashed");
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("dev@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    /** The message must match the wrong-password case so accounts cannot be enumerated. */
    @Test
    void rejectsAnUnknownEmailWithTheSameMessage() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "password123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void looksUpTheCurrentUser() {
        User user = new User("dev@example.com", "Dev", "dev", "hashed");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(authService.currentUser(user.getId()).displayName()).isEqualTo("Dev");
    }

    @Test
    void reportsNotFoundForAMissingCurrentUser() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.currentUser(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void givenTokenIssued() {
        when(accessTokenIssuer.issue(any()))
                .thenReturn(new IssuedToken("issued-token", Instant.now().plusSeconds(3600)));
    }
}
