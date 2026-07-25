package com.devforge.identity.application;

import com.devforge.identity.domain.User;
import com.devforge.identity.domain.UserRepository;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @InjectMocks
    private AuthService authService;

    @Test
    void registersAUserWithAHashedPassword() {
        when(userRepository.existsByEmail("dev@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        givenTokenIssued();

        AuthenticationResponse response = authService.register(
                new RegisterRequest("dev@example.com", "Dev", "password123"));

        assertThat(response.accessToken()).isEqualTo("issued-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("dev@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash())
                .as("raw password must never be stored")
                .isEqualTo("hashed");
    }

    @Test
    void normalisesEmailAndTrimsNameOnRegistration() {
        when(userRepository.existsByEmail("dev@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        givenTokenIssued();

        authService.register(new RegisterRequest("  Dev@Example.COM  ", "  Dev Name  ", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("dev@example.com");
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Dev Name");
    }

    @Test
    void rejectsAnAlreadyRegisteredEmail() {
        when(userRepository.existsByEmail("dev@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("dev@example.com", "Dev", "password123")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void logsInWithCorrectCredentials() {
        User user = new User("dev@example.com", "Dev", "hashed");
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        givenTokenIssued();

        assertThat(authService.login(new LoginRequest("dev@example.com", "password123")).accessToken())
                .isEqualTo("issued-token");
    }

    @Test
    void rejectsAWrongPassword() {
        User user = new User("dev@example.com", "Dev", "hashed");
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
        User user = new User("dev@example.com", "Dev", "hashed");
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
