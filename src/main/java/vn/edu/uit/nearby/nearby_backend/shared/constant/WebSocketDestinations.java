package vn.edu.uit.nearby.nearby_backend.shared.constant;

public final class WebSocketDestinations {

	public static final String TOPIC_PREFIX = "/topic";

	public static final String QUEUE_PREFIX = "/queue";

	public static final String APPLICATION_PREFIX = "/app";

	public static final String USER_PREFIX = "/user";

	/**
	 * Per-user queues (FR-CH-02, FR-CH-06, FR-NT-06); the client subscribes to
	 * {@code /user/queue/...}.
	 */
	public static final String QUEUE_MESSAGES = QUEUE_PREFIX + "/messages";

	public static final String QUEUE_ACKS = QUEUE_PREFIX + "/acks";

	public static final String QUEUE_READ_RECEIPTS = QUEUE_PREFIX + "/read-receipts";

	public static final String QUEUE_BADGES = QUEUE_PREFIX + "/badges";

	public static final String QUEUE_NOTIFICATIONS = QUEUE_PREFIX + "/notifications";

	public static final String QUEUE_ERRORS = QUEUE_PREFIX + "/errors";

	private WebSocketDestinations() {
	}

}
