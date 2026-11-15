package vn.edu.uit.nearby.nearby_backend.identity.application.port;

import java.util.UUID;

/**
 * @param boundTo the phone number or email address the token was sent to, so a token
 * stops counting once the account's address changes
 */
public record OneTimeTokenSubject(UUID userId, String boundTo) {

}
