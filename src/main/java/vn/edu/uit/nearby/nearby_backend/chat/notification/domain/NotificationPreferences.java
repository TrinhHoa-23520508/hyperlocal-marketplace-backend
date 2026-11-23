package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * A user's push switches per category and quiet hours (FR-NT-03). They only filter the
 * push: the in-app notification is always created. SYSTEM has no switch because it cannot
 * be turned off.
 *
 * @param version optimistic lock of {@code notification_preferences.version}
 */
public record NotificationPreferences(UUID userId, boolean chatPush, boolean tradePush, boolean keywordAlertPush,
		boolean marketingPush, boolean dndEnabled, LocalTime dndStart, LocalTime dndEnd, ZoneId timeZone,
		long version) {

	public static final LocalTime DEFAULT_QUIET_START = LocalTime.of(22, 0);

	public static final LocalTime DEFAULT_QUIET_END = LocalTime.of(7, 0);

	public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	/**
	 * What a user who never saved preferences gets, and what the row created on
	 * registration holds.
	 */
	public static NotificationPreferences defaults(UUID userId) {
		return new NotificationPreferences(userId, true, true, true, false, false, DEFAULT_QUIET_START,
				DEFAULT_QUIET_END, DEFAULT_ZONE, 0);
	}

	/**
	 * Everything the user can set on A30; a PUT replaces all of it.
	 *
	 * @param systemPush must be true: SYSTEM cannot be turned off
	 * @param timeZone an IANA zone id such as {@code Asia/Ho_Chi_Minh}
	 */
	public record Change(boolean chatPush, boolean tradePush, boolean keywordAlertPush, boolean marketingPush,
			boolean systemPush, boolean dndEnabled, LocalTime dndStart, LocalTime dndEnd, String timeZone) {
	}

	/**
	 * @param marketingConsent whether the PUSH_MARKETING consent is granted now
	 * @throws InvalidRequestException {@code VALIDATION_FAILED}: SYSTEM switched off,
	 * MARKETING switched on without consent, an empty quiet window or an unknown time
	 * zone
	 */
	public NotificationPreferences apply(Change change, boolean marketingConsent) {
		if (!change.systemPush()) {
			throw InvalidRequestException.invalidField("systemPush", "SYSTEM notifications cannot be turned off.");
		}
		if (change.marketingPush() && !marketingConsent) {
			throw InvalidRequestException.invalidField("marketingPush",
					"Marketing notifications need the push marketing consent.");
		}
		if (change.dndStart().equals(change.dndEnd())) {
			throw InvalidRequestException.invalidField("dndEnd", "The quiet hours must not start and end together.");
		}
		return new NotificationPreferences(userId, change.chatPush(), change.tradePush(), change.keywordAlertPush(),
				change.marketingPush(), change.dndEnabled(), change.dndStart(), change.dndEnd(),
				zoneOf(change.timeZone()), version);
	}

	/**
	 * @return whether the push switch of the category is on; SYSTEM is always on
	 */
	public boolean pushEnabledFor(NotificationCategory category) {
		return switch (category) {
			case CHAT -> chatPush;
			case TRADE -> tradePush;
			case KEYWORD_ALERT -> keywordAlertPush;
			case MARKETING -> marketingPush;
			case SYSTEM -> true;
		};
	}

	/**
	 * Quiet hours start at {@code dndStart} (included) and end at {@code dndEnd}
	 * (excluded); the window may cross midnight (data dictionary).
	 */
	public boolean isQuietAt(Instant moment) {
		if (!dndEnabled) {
			return false;
		}
		LocalTime local = moment.atZone(timeZone).toLocalTime();
		if (dndStart.isBefore(dndEnd)) {
			return !local.isBefore(dndStart) && local.isBefore(dndEnd);
		}
		return !local.isBefore(dndStart) || local.isBefore(dndEnd);
	}

	private static ZoneId zoneOf(String id) {
		try {
			return ZoneId.of(id);
		}
		catch (DateTimeException ex) {
			throw InvalidRequestException.invalidField("timeZone", "Unknown time zone.");
		}
	}

}
