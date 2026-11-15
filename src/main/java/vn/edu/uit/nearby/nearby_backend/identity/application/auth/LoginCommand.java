package vn.edu.uit.nearby.nearby_backend.identity.application.auth;

/**
 * @param identifier the phone number, or an email address the user verified
 */
public record LoginCommand(String identifier, String password) {

}
