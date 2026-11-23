package vn.edu.uit.nearby.nearby_backend.shared.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.constant.WebSocketDestinations;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	/**
	 * Replicas tell each other which user sessions they hold, so a message for a user is
	 * routed to the replica where that user is connected.
	 */
	static final String USER_REGISTRY_BROADCAST = WebSocketDestinations.TOPIC_PREFIX + "/user-registry";

	static final String UNRESOLVED_USER_DESTINATION_BROADCAST = WebSocketDestinations.TOPIC_PREFIX
			+ "/unresolved-user-destination";

	private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

	private final WebSocketRelayProperties relay;

	public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor, WebSocketRelayProperties relay) {
		this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
		this.relay = relay;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// Native Android client only: there is no browser Origin to restrict.
		registry.addEndpoint(ApiPaths.WEBSOCKET_ENDPOINT).setAllowedOriginPatterns("*");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		if (relay.enabled()) {
			registry.enableStompBrokerRelay(WebSocketDestinations.TOPIC_PREFIX, WebSocketDestinations.QUEUE_PREFIX)
				.setRelayHost(relay.host())
				.setRelayPort(relay.port())
				.setClientLogin(relay.clientLogin())
				.setClientPasscode(relay.clientPasscode())
				.setSystemLogin(relay.systemLogin())
				.setSystemPasscode(relay.systemPasscode())
				.setVirtualHost(relay.virtualHost())
				.setUserRegistryBroadcast(USER_REGISTRY_BROADCAST)
				.setUserDestinationBroadcast(UNRESOLVED_USER_DESTINATION_BROADCAST);
		}
		else {
			// One instance only: tests and a single-node development run.
			registry.enableSimpleBroker(WebSocketDestinations.TOPIC_PREFIX, WebSocketDestinations.QUEUE_PREFIX);
		}
		registry.setApplicationDestinationPrefixes(WebSocketDestinations.APPLICATION_PREFIX);
		registry.setUserDestinationPrefix(WebSocketDestinations.USER_PREFIX);
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthChannelInterceptor);
	}

}
