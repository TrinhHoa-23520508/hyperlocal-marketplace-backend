package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Whether a push goes out for a notification (FR-NT-02, FR-NT-03). A skipped push leaves
 * {@code push_status} SKIPPED; the in-app row exists either way.
 *
 * @param skipReason null when the push is sent
 */
public record PushDecision(@Nullable SkipReason skipReason) {

	/**
	 * In the order the checks are made.
	 */
	public enum SkipReason {

		/**
		 * The member muted the conversation (FR-CH-09, R2: never true in R1).
		 */
		CONVERSATION_MUTED,

		CATEGORY_OFF, QUIET_HOURS, NO_DEVICE_TOKEN

	}

	private static final PushDecision SEND = new PushDecision(null);

	public static PushDecision decide(NotificationPreferences preferences, NotificationCategory category,
			boolean conversationMuted, boolean hasDeviceToken, Instant now) {
		if (conversationMuted) {
			return skip(SkipReason.CONVERSATION_MUTED);
		}
		if (!preferences.pushEnabledFor(category)) {
			return skip(SkipReason.CATEGORY_OFF);
		}
		if (preferences.isQuietAt(now)) {
			return skip(SkipReason.QUIET_HOURS);
		}
		return hasDeviceToken ? SEND : skip(SkipReason.NO_DEVICE_TOKEN);
	}

	public boolean shouldSend() {
		return skipReason == null;
	}

	private static PushDecision skip(SkipReason reason) {
		return new PushDecision(reason);
	}

}
