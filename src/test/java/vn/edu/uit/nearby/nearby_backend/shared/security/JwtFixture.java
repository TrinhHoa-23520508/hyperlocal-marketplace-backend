package vn.edu.uit.nearby.nearby_backend.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

public final class JwtFixture {

	public static final JwtProperties PROPERTIES = new JwtProperties("test-secret-that-is-at-least-32-bytes!!",
			"nearby-test", Duration.ofMinutes(15), Duration.ofDays(30));

	private static final JwtConfig CONFIG = new JwtConfig();

	private JwtFixture() {
	}

	public static JwtAuthenticationConverter authenticationConverter() {
		return CONFIG.jwtAuthenticationConverter();
	}

	public static JwtDecoder decoder() {
		return CONFIG.jwtDecoder(CONFIG.jwtSigningKey(PROPERTIES), PROPERTIES, List.of());
	}

	/**
	 * Decoder whose expiry check uses {@code clock}, for tokens issued at a fixed test
	 * time.
	 */
	public static JwtDecoder tokenDecoderAt(Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(CONFIG.jwtSigningKey(PROPERTIES))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(clock);
		decoder.setJwtValidator(timestamps);
		return decoder;
	}

	public static TokenService tokenService(JwtProperties properties, Clock clock) {
		return new TokenService(CONFIG.jwtEncoder(CONFIG.jwtSigningKey(properties)), properties, clock);
	}

	public static String accessToken(String role) {
		return tokenService(PROPERTIES, Clock.systemUTC()).issueAccessToken(UUID.randomUUID(), role).value();
	}

}
