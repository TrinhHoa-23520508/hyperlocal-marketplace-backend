package vn.edu.uit.nearby.nearby_backend.identity.infrastructure;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenStore;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenSubject;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.SecurityConstants;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;

@Component
public class RedisOneTimeTokenStore implements OneTimeTokenStore {

	private static final String SEPARATOR = "|";

	private final StringRedisTemplate redis;

	private final SecureRandom secureRandom = new SecureRandom();

	public RedisOneTimeTokenStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public String issue(OneTimeTokenPurpose purpose, OneTimeTokenSubject subject, Duration ttl) {
		String token = randomToken();
		redis.opsForValue().set(keyOf(purpose, token), subject.userId() + SEPARATOR + subject.boundTo(), ttl);
		return token;
	}

	@Override
	public Optional<OneTimeTokenSubject> find(OneTimeTokenPurpose purpose, String token) {
		return Optional.ofNullable(redis.opsForValue().get(keyOf(purpose, token))).map(RedisOneTimeTokenStore::parse);
	}

	@Override
	public Optional<OneTimeTokenSubject> consume(OneTimeTokenPurpose purpose, String token) {
		return Optional.ofNullable(redis.opsForValue().getAndDelete(keyOf(purpose, token)))
			.map(RedisOneTimeTokenStore::parse);
	}

	private String randomToken() {
		byte[] bytes = new byte[SecurityConstants.REFRESH_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static OneTimeTokenSubject parse(String stored) {
		int separator = stored.indexOf(SEPARATOR);
		return new OneTimeTokenSubject(UUID.fromString(stored.substring(0, separator)),
				stored.substring(separator + 1));
	}

	private static String keyOf(OneTimeTokenPurpose purpose, String token) {
		String prefix = switch (purpose) {
			case EMAIL_VERIFICATION -> RedisKeyPrefixes.EMAIL_VERIFICATION_TOKEN;
			case PASSWORD_RESET -> RedisKeyPrefixes.PASSWORD_RESET_TOKEN;
		};
		return prefix + TokenService.sha256Hex(token);
	}

}
