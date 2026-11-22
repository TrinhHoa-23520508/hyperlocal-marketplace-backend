package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountIdentifier;

public record LoginAttempt(AccountIdentifier identifier, String clientIp) {

}
