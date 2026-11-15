package vn.edu.uit.nearby.nearby_backend.shared.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Browser origins allowed to call the API, such as the admin console. The Android app is
 * not a browser and needs no entry. An empty list turns CORS off.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins, @DefaultValue("1h") Duration maxAge) {

}
