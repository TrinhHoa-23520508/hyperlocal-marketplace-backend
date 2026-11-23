package vn.edu.uit.nearby.nearby_backend.chat.infrastructure;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import vn.edu.uit.nearby.nearby_backend.chat.application.UserPresence;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;

/**
 * Presence kept in Redis so every replica sees it. Each user has a sorted set of their
 * session ids scored by when the session lapses; a replica renews only the sessions it
 * holds, so the sessions of a crashed replica lapse by themselves within
 * {@link #SESSION_TTL}.
 */
@Component
class RedisUserPresence implements UserPresence {

	static final Duration SESSION_TTL = Duration.ofSeconds(90);

	private static final Logger log = LoggerFactory.getLogger(RedisUserPresence.class);

	private final StringRedisTemplate redis;

	private final Clock clock;

	private final Map<String, UUID> localSessions = new ConcurrentHashMap<>();

	RedisUserPresence(StringRedisTemplate redis, Clock clock, MeterRegistry meters) {
		this.redis = redis;
		this.clock = clock;
		// Monitoring (NFR-20, ch6): WebSocket connections per replica
		Gauge.builder("nearby.websocket.sessions", localSessions, Map::size)
			.description("STOMP sessions connected to this replica")
			.register(meters);
	}

	@Override
	public boolean isOnline(UUID userId) {
		Long live = redis.opsForZSet().count(key(userId), clock.millis(), Double.POSITIVE_INFINITY);
		return live != null && live > 0;
	}

	@EventListener
	void onConnected(SessionConnectedEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		UUID userId = userOf(event.getUser());
		String sessionId = accessor.getSessionId();
		if (userId != null && sessionId != null) {
			localSessions.put(sessionId, userId);
			touch(sessionId, userId);
		}
	}

	@EventListener
	void onDisconnected(SessionDisconnectEvent event) {
		UUID userId = localSessions.remove(event.getSessionId());
		if (userId != null) {
			redis.opsForZSet().remove(key(userId), event.getSessionId());
		}
	}

	@Scheduled(fixedDelay = 30_000)
	void renewLocalSessions() {
		localSessions.forEach((sessionId, userId) -> {
			try {
				touch(sessionId, userId);
			}
			catch (RuntimeException ex) {
				log.warn("Could not renew presence of {}: {}", userId, ex.toString());
			}
		});
	}

	private void touch(String sessionId, UUID userId) {
		long now = clock.millis();
		String key = key(userId);
		redis.opsForZSet().add(key, sessionId, now + SESSION_TTL.toMillis());
		redis.opsForZSet().removeRangeByScore(key, 0, now);
		redis.expire(key, SESSION_TTL.multipliedBy(2));
	}

	private static @Nullable UUID userOf(@Nullable Principal principal) {
		try {
			return principal == null ? null : UUID.fromString(principal.getName());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static String key(UUID userId) {
		return RedisKeyPrefixes.CHAT_PRESENCE + userId;
	}

}
