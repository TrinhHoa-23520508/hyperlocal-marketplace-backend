package vn.edu.uit.nearby.nearby_backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import vn.edu.uit.nearby.nearby_backend.NearbyBackendApplication;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageWriter.MessageStored;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AccountStanding;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;
import vn.edu.uit.nearby.nearby_backend.listing.ListingSnapshot;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageType;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.shared.constant.Roles;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;

/**
 * FR-CH-02 acceptance: with two application replicas behind one RabbitMQ broker relay
 * (ADR-13), a message pushed by replica 1 reaches the recipient connected to replica 2.
 * Both replicas share one PostgreSQL, one Redis and one RabbitMQ, as in production.
 */
class ChatRelayIntegrationTests {

	private static final String RABBIT_USER = "nearby";

	private static final String RABBIT_PASSWORD = "nearby-secret";

	private static PostgreSQLContainer postgres;

	private static GenericContainer<?> redis;

	private static GenericContainer<?> rabbit;

	private static ConfigurableApplicationContext replica1;

	private static ConfigurableApplicationContext replica2;

	private final UUID buyer = UUID.randomUUID();

	private final UUID seller = UUID.randomUUID();

	private final Conversation conversation = new Conversation(UUID.randomUUID(), UUID.randomUUID(), buyer, seller);

	@BeforeAll
	static void startReplicas() {
		postgres = new PostgreSQLContainer(
				DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));
		redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
		rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:4-management-alpine")).withExposedPorts(61613)
			.withEnv("RABBITMQ_DEFAULT_USER", RABBIT_USER)
			.withEnv("RABBITMQ_DEFAULT_PASS", RABBIT_PASSWORD)
			.withCommand("sh", "-c",
					"rabbitmq-plugins enable --offline rabbitmq_stomp && exec docker-entrypoint.sh rabbitmq-server")
			.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
		postgres.start();
		redis.start();
		rabbit.start();
		replica1 = startReplica();
		replica2 = startReplica();
	}

	@AfterAll
	static void stopReplicas() {
		for (ConfigurableApplicationContext replica : new ConfigurableApplicationContext[] { replica2, replica1 }) {
			if (replica != null) {
				replica.close();
			}
		}
		for (GenericContainer<?> container : new GenericContainer<?>[] { rabbit, redis, postgres }) {
			if (container != null) {
				container.stop();
			}
		}
	}

	private static ConfigurableApplicationContext startReplica() {
		// Command line arguments beat application.yml; builder properties would lose to
		// it.
		return new SpringApplicationBuilder(NearbyBackendApplication.class, ChatUpstreamFacades.class)
			.web(WebApplicationType.SERVLET)
			.run("--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl(),
					"--spring.datasource.username=" + postgres.getUsername(),
					"--spring.datasource.password=" + postgres.getPassword(),
					"--spring.data.redis.host=" + redis.getHost(),
					"--spring.data.redis.port=" + redis.getMappedPort(6379), "--app.websocket.relay.enabled=true",
					"--app.websocket.relay.host=" + rabbit.getHost(),
					"--app.websocket.relay.port=" + rabbit.getMappedPort(61613),
					"--app.websocket.relay.client-login=" + RABBIT_USER,
					"--app.websocket.relay.client-passcode=" + RABBIT_PASSWORD,
					"--app.websocket.relay.system-login=" + RABBIT_USER,
					"--app.websocket.relay.system-passcode=" + RABBIT_PASSWORD, "--spring.main.banner-mode=off");
	}

	@Test
	@DisplayName("FR-CH-02: a message pushed by replica 1 reaches the recipient connected to replica 2")
	void aMessageCrossesReplicas() throws Exception {
		BlockingQueue<String> received = new LinkedBlockingQueue<>();
		StompSession recipient = connect(replica2, buyer);
		subscribe(recipient, "/user/queue/messages", received);

		replica1.getBean(ChatRealtime.class).onMessageStored(new MessageStored(conversation, message(), false));

		String json = received.poll(15, TimeUnit.SECONDS);
		assertThat(json).isNotNull().contains("Còn hàng không bạn?").contains("\"type\":\"TEXT\"");
		recipient.disconnect();
	}

	@Test
	@DisplayName("UC-CH-02 step 5: the acknowledgement of a socket send reaches the sender on any replica")
	void theSenderIsAcknowledgedOnAnotherReplica() throws Exception {
		BlockingQueue<String> acks = new LinkedBlockingQueue<>();
		StompSession sender = connect(replica2, seller);
		subscribe(sender, "/user/queue/acks", acks);

		replica1.getBean(ChatRealtime.class).onMessageStored(new MessageStored(conversation, message(), true));

		assertThat(acks.poll(15, TimeUnit.SECONDS)).isNotNull().contains("\"id\":42");
		sender.disconnect();
	}

	@Test
	@DisplayName("FR-NT-01: a user connected to one replica is online for every replica, and offline again after")
	void presenceIsSharedBetweenReplicas() throws Exception {
		UserPresence seenFromReplica1 = replica1.getBean(UserPresence.class);
		assertThat(seenFromReplica1.isOnline(buyer)).isFalse();

		StompSession session = connect(replica2, buyer);

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(seenFromReplica1.isOnline(buyer)).isTrue());
		// ch6 monitoring: the replica that holds the session reports it
		assertThat(replica2.getBean(io.micrometer.core.instrument.MeterRegistry.class)
			.get("nearby.websocket.sessions")
			.gauge()
			.value()).isGreaterThanOrEqualTo(1);
		session.disconnect();
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(seenFromReplica1.isOnline(buyer)).isFalse());
	}

	/**
	 * A conversation about an ACTIVE listing between two active users, known to both
	 * replicas.
	 */
	private Conversation givenConversation() {
		UUID listing = UUID.randomUUID();
		UUID area = UUID.randomUUID();
		for (ConfigurableApplicationContext replica : new ConfigurableApplicationContext[] { replica1, replica2 }) {
			ListingSnapshot snapshot = new ListingSnapshot(listing, seller, area, "Ghế gỗ", "ACTIVE", 150_000, true,
					new GeoPoint(10.77, 106.70), null, null);
			Mockito.when(replica.getBean(ListingLookup.class).find(listing)).thenReturn(Optional.of(snapshot));
			Mockito.when(replica.getBean(AccountStanding.class).isActiveWithVerifiedPhone(Mockito.any()))
				.thenReturn(true);
		}
		return replica1.getBean(ConversationRepository.class)
			.insertIfAbsent(listing, buyer, seller, Instant.now())
			.conversation();
	}

	private static void sendFrame(StompSession session, String destination, String json) {
		StompHeaders headers = new StompHeaders();
		headers.setDestination(destination);
		headers.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
		session.send(headers, json);
	}

	private static String textBody(UUID clientMsgId, String body) {
		return "{\"clientMsgId\":\"" + clientMsgId + "\",\"type\":\"TEXT\",\"body\":\"" + body + "\"}";
	}

	@Test
	@DisplayName("FR-CH-02: SEND over STOMP on replica 1 reaches the recipient on replica 2 and is acknowledged")
	void aSocketSendIsDeliveredAndAcknowledged() throws Exception {
		Conversation chat = givenConversation();
		BlockingQueue<String> acks = new LinkedBlockingQueue<>();
		BlockingQueue<String> received = new LinkedBlockingQueue<>();
		StompSession sender = connect(replica1, buyer);
		StompSession recipient = connect(replica2, seller);
		subscribe(sender, "/user/queue/acks", acks);
		subscribe(recipient, "/user/queue/messages", received);
		UUID clientMsgId = UUID.randomUUID();

		sendFrame(sender, "/app/conversations/" + chat.id() + "/messages",
				textBody(clientMsgId, "Còn hàng không bạn?"));

		assertThat(received.poll(15, TimeUnit.SECONDS)).isNotNull()
			.contains("Còn hàng không bạn?")
			.contains(clientMsgId.toString());
		assertThat(acks.poll(15, TimeUnit.SECONDS)).isNotNull().contains(clientMsgId.toString());
		sender.disconnect();
		recipient.disconnect();
	}

	@Test
	@DisplayName("UC-CH-02 A3: sending the same clientMsgId again is acknowledged with the stored message")
	void aRepeatedSocketSendIsAcknowledgedAgain() throws Exception {
		Conversation chat = givenConversation();
		BlockingQueue<String> acks = new LinkedBlockingQueue<>();
		StompSession sender = connect(replica1, buyer);
		subscribe(sender, "/user/queue/acks", acks);
		UUID clientMsgId = UUID.randomUUID();

		sendFrame(sender, "/app/conversations/" + chat.id() + "/messages", textBody(clientMsgId, "một"));
		String first = acks.poll(15, TimeUnit.SECONDS);
		sendFrame(sender, "/app/conversations/" + chat.id() + "/messages", textBody(clientMsgId, "một"));
		String second = acks.poll(15, TimeUnit.SECONDS);

		assertThat(first).isNotNull();
		assertThat(second).isNotNull();
		assertThat(idOf(second)).isEqualTo(idOf(first));
		assertThat(replica1.getBean(MessageRepository.class).page(chat.id(), null, null, 10)).hasSize(1);
		sender.disconnect();
	}

	@Test
	@DisplayName("UC-CH-02 E1: a failed send goes to /user/queue/errors with the REST body and the clientMsgId")
	void errorsGoToTheErrorQueue() throws Exception {
		Conversation chat = givenConversation();
		BlockingQueue<String> errors = new LinkedBlockingQueue<>();
		StompSession sender = connect(replica1, buyer);
		subscribe(sender, "/user/queue/errors", errors);
		UUID tooLong = UUID.randomUUID();
		UUID blank = UUID.randomUUID();

		sendFrame(sender, "/app/conversations/" + chat.id() + "/messages", textBody(tooLong, "a".repeat(1_001)));
		String lengthError = errors.poll(15, TimeUnit.SECONDS);
		sendFrame(sender, "/app/conversations/" + chat.id() + "/messages", textBody(blank, "  "));
		String blankError = errors.poll(15, TimeUnit.SECONDS);

		assertThat(lengthError).isNotNull()
			.contains("\"code\":\"MESSAGE_TOO_LONG\"")
			.contains("\"status\":422")
			.contains("\"clientMsgId\":\"" + tooLong + "\"");
		assertThat(blankError).isNotNull()
			.contains("\"code\":\"VALIDATION_FAILED\"")
			.contains("\"clientMsgId\":\"" + blank + "\"");
		sender.disconnect();
	}

	@Test
	@DisplayName("UC-CH-02 E4: a stranger and a payload without a type are refused")
	void strangersAndBadPayloadsAreRefused() throws Exception {
		Conversation chat = givenConversation();
		BlockingQueue<String> errors = new LinkedBlockingQueue<>();
		StompSession stranger = connect(replica1, UUID.randomUUID());
		subscribe(stranger, "/user/queue/errors", errors);

		sendFrame(stranger, "/app/conversations/" + chat.id() + "/messages", textBody(UUID.randomUUID(), "xin chào"));
		String notMember = errors.poll(15, TimeUnit.SECONDS);
		sendFrame(stranger, "/app/conversations/" + chat.id() + "/messages",
				"{\"clientMsgId\":\"" + UUID.randomUUID() + "\",\"body\":\"thiếu type\"}");
		String invalid = errors.poll(15, TimeUnit.SECONDS);

		assertThat(notMember).isNotNull().contains("\"code\":\"CONVERSATION_NOT_FOUND\"");
		assertThat(invalid).isNotNull().contains("\"code\":\"VALIDATION_FAILED\"").contains("\"field\":\"type\"");
		stranger.disconnect();
	}

	@Test
	@DisplayName("FR-CH-06 / FR-NT-06: READ over STOMP sends a receipt to the counterpart and the badge to the reader")
	void aSocketReadSendsAReceiptAndTheBadge() throws Exception {
		Conversation chat = givenConversation();
		StoredMessage unread = replica1.getBean(MessageRepository.class)
			.insert(chat.id(), buyer, MessageContent.text("Còn hàng không bạn?", 1000), UUID.randomUUID(),
					Instant.now())
			.message();
		replica1.getBean(ConversationRepository.class).addUnreadForOthers(chat.id(), buyer);
		BlockingQueue<String> receipts = new LinkedBlockingQueue<>();
		BlockingQueue<String> badges = new LinkedBlockingQueue<>();
		StompSession counterpart = connect(replica1, buyer);
		StompSession reader = connect(replica2, seller);
		subscribe(counterpart, "/user/queue/read-receipts", receipts);
		subscribe(reader, "/user/queue/badges", badges);

		sendFrame(reader, "/app/conversations/" + chat.id() + "/read", "{\"lastReadMessageId\":" + unread.id() + "}");

		assertThat(receipts.poll(15, TimeUnit.SECONDS)).isNotNull()
			.contains("\"lastReadMessageId\":" + unread.id())
			.contains(seller.toString());
		assertThat(badges.poll(15, TimeUnit.SECONDS)).isNotNull().contains("\"chatUnread\":0");
		counterpart.disconnect();
		reader.disconnect();
	}

	@Test
	@DisplayName("UC-CH-02 E6: a CONNECT with an expired or tampered access token is refused")
	void anExpiredTokenCannotConnect() throws Exception {
		Instant now = Instant.now();
		JwtClaimsSet expired = JwtClaimsSet.builder()
			.issuer("nearby-backend")
			.subject(buyer.toString())
			.issuedAt(now.minusSeconds(7_200))
			.expiresAt(now.minusSeconds(3_600))
			.claim("roles", List.of(Roles.USER))
			.build();
		String expiredToken = replica1.getBean(JwtEncoder.class)
			.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), expired))
			.getTokenValue();
		String validToken = replica1.getBean(TokenService.class).issueAccessToken(buyer, Roles.USER).value();

		assertThat(connectionFails(replica1, expiredToken)).isTrue();
		assertThat(connectionFails(replica1, "garbage")).isTrue();
		assertThat(connectionFails(replica1, validToken)).isFalse();
	}

	private boolean connectionFails(ConfigurableApplicationContext replica, String token) throws Exception {
		String port = replica.getEnvironment().getProperty("local.server.port");
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.initialize();
		client.setTaskScheduler(scheduler);
		StompHeaders headers = new StompHeaders();
		headers.add("Authorization", "Bearer " + token);
		CompletableFuture<StompSession> connected = new CompletableFuture<>();
		client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
				new StompSessionHandlerAdapter() {
					@Override
					public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
						connected.complete(session);
					}

					@Override
					public void handleException(StompSession session,
							org.springframework.messaging.simp.stomp.StompCommand command, StompHeaders stompHeaders,
							byte[] payload, Throwable exception) {
						connected.completeExceptionally(exception);
					}

					@Override
					public void handleTransportError(StompSession session, Throwable exception) {
						connected.completeExceptionally(exception);
					}
				});
		try {
			connected.get(10, TimeUnit.SECONDS).disconnect();
			return false;
		}
		catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
			return true;
		}
	}

	private static String idOf(String ackJson) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(ackJson);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private StoredMessage message() {
		return new StoredMessage(42, conversation.id(), seller, MessageType.TEXT, "Còn hàng không bạn?", null,
				UUID.randomUUID(), Instant.parse("2026-11-23T10:00:00Z"));
	}

	private StompSession connect(ConfigurableApplicationContext replica, UUID userId) throws Exception {
		String port = replica.getEnvironment().getProperty("local.server.port");
		String token = replica.getBean(TokenService.class).issueAccessToken(userId, Roles.USER).value();
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new RawJsonConverter());
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.initialize();
		client.setTaskScheduler(scheduler);
		StompHeaders headers = new StompHeaders();
		headers.add("Authorization", "Bearer " + token);
		CompletableFuture<StompSession> connected = client.connectAsync("ws://localhost:" + port + "/ws",
				new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {
				});
		StompSession session = connected.get(15, TimeUnit.SECONDS);
		session.setAutoReceipt(true);
		return session;
	}

	private static void subscribe(StompSession session, String destination, BlockingQueue<String> into)
			throws InterruptedException {
		CountDownLatch subscribed = new CountDownLatch(1);
		StompSession.Subscription subscription = session.subscribe(destination, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return String.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				into.add((String) payload);
			}
		});
		subscription.addReceiptTask(subscribed::countDown);
		assertThat(subscribed.await(15, TimeUnit.SECONDS)).as("the broker confirmed the subscription").isTrue();
	}

	/**
	 * Hands the JSON frames to the test as text, whatever their content type says.
	 */
	private static final class RawJsonConverter implements MessageConverter {

		@Override
		public Object fromMessage(Message<?> message, Class<?> targetClass) {
			Object payload = message.getPayload();
			return payload instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
					: String.valueOf(payload);
		}

		@Override
		public Message<?> toMessage(Object payload, MessageHeaders headers) {
			byte[] bytes = String.valueOf(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
			return org.springframework.messaging.support.MessageBuilder.createMessage(bytes, headers);
		}

	}

}
