package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.time.Instant;

public record PasswordResetToken(String token, Instant expiresAt) {

}
