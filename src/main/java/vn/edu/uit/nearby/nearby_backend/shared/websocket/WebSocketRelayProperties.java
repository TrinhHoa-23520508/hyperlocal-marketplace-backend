package vn.edu.uit.nearby.nearby_backend.shared.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The RabbitMQ STOMP broker relay that lets a message reach a user connected to another
 * application replica (FR-CH-02, NFR-20, ADR-13). When {@code enabled} is false the
 * in-memory broker is used, which is only correct for one instance.
 *
 * @param clientLogin login the app instance uses for the sessions it relays
 * @param systemLogin login of the single shared connection that sends server-originated
 * messages
 */
@ConfigurationProperties("app.websocket.relay")
public record WebSocketRelayProperties(boolean enabled, String host, int port, String clientLogin,
		String clientPasscode, String systemLogin, String systemPasscode, String virtualHost) {
}
