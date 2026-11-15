package vn.edu.uit.nearby.nearby_backend.shared.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled false writes emails to the log instead of sending them (local
 * development)
 * @param from sender address, for example {@code Nearby <no-reply@example.com>}
 */
@ConfigurationProperties("app.mail")
public record EmailProperties(boolean enabled, String from) {

}
