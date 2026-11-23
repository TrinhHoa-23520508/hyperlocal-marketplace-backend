package vn.edu.uit.nearby.nearby_backend.shared.persistence;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Connection to the read path that serves read-only transactions (FR-DC-12, ADR-11). An
 * empty {@code url} means there is no read replica: read-only transactions use the
 * primary.
 *
 * @param connectionTimeout how long to wait for a replica connection before falling back
 * to the primary
 */
public record ReadPathProperties(@Nullable String url, @Nullable String username, @Nullable String password,
		@DefaultValue("10") int maximumPoolSize, @DefaultValue("2s") Duration connectionTimeout) {

	public static final String PREFIX = "app.datasource.read";

	public boolean isConfigured() {
		return StringUtils.hasText(url);
	}

}
