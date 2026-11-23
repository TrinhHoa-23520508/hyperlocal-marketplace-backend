package vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PushGateway;

/**
 * Used while {@code app.push.fcm.credentials} is empty: the push is written to the log
 * and reported as disabled, so the notification shows SKIPPED instead of pretending it
 * was delivered.
 */
public class LoggingPushGateway implements PushGateway {

	private static final Logger log = LoggerFactory.getLogger(LoggingPushGateway.class);

	@Override
	public PushResult send(String deviceToken, PushMessage message) {
		log.info("Push disabled (app.push.fcm.credentials is empty): \"{}\" -> {}", message.title(),
				message.deepLink());
		return PushResult.DISABLED;
	}

}
