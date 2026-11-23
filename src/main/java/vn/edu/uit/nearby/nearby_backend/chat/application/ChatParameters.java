package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Duration;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageUploadPolicy;

/**
 * Typed access to the chat runtime parameters (SRS table {@code tab:params}, seeded by
 * V15). Read on every call so a changed value applies without a restart.
 */
@Component
public class ChatParameters {

	private static final String MAX_MESSAGE_CHARS = "chat.max_message_chars";

	private static final String RATE_LIMIT_PER_MIN = "chat.rate_limit_per_min";

	private static final String RETENTION_MONTHS = "notification.retention_months";

	private static final String IMAGE_MAX_BYTES = "image.max_bytes";

	private static final String ORPHAN_TTL_HOURS = "upload.orphan_ttl_hours";

	private final AppParameters parameters;

	public ChatParameters(AppParameters parameters) {
		this.parameters = parameters;
	}

	public int maxMessageChars() {
		return parameters.getInt(MAX_MESSAGE_CHARS);
	}

	public int messagesPerMinute() {
		return parameters.getInt(RATE_LIMIT_PER_MIN);
	}

	public int notificationRetentionMonths() {
		return parameters.getInt(RETENTION_MONTHS);
	}

	/**
	 * FR-CH-04: each image at most {@code image.max_bytes} (5 MB).
	 */
	public ImageUploadPolicy imageUploadPolicy() {
		return new ImageUploadPolicy(parameters.getInt(IMAGE_MAX_BYTES));
	}

	/**
	 * UC-SY-15: how long an uploaded chat image may stay unused.
	 */
	public Duration orphanImageTtl() {
		return Duration.ofHours(parameters.getInt(ORPHAN_TTL_HOURS));
	}

}
