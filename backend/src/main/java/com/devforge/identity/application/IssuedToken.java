package com.devforge.identity.application;

import java.time.Instant;

public record IssuedToken(String token, Instant expiresAt) {
}
