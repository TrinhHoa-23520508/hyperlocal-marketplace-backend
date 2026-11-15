package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.identity.application.auth.TokenPair;

public record RegisteredAccount(UUID userId, String phone, TokenPair tokens) {

}
