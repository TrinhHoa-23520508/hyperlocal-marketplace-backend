package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences.Change;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationPreferencesRepository;
import vn.edu.uit.nearby.nearby_backend.identity.MarketingConsent;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

/**
 * UC-NT-02 (A30): the push switches per category and the quiet hours (FR-NT-03). A user
 * who registered before the table existed, or whose row was never created, reads the
 * defaults, and the row is created on the first save.
 */
@Service
public class PreferencesService {

	private final NotificationPreferencesRepository preferences;

	private final MarketingConsent marketingConsent;

	private final Clock clock;

	PreferencesService(NotificationPreferencesRepository preferences, MarketingConsent marketingConsent, Clock clock) {
		this.preferences = preferences;
		this.marketingConsent = marketingConsent;
		this.clock = clock;
	}

	/**
	 * @param systemPush always true: SYSTEM cannot be turned off
	 * @param marketingConsentGranted whether {@code marketingPush} may be switched on
	 * @param version send it back on save; a stale one is answered 409
	 */
	public record PreferencesView(boolean chatPush, boolean tradePush, boolean keywordAlertPush, boolean marketingPush,
			boolean systemPush, boolean dndEnabled, @Schema(example = "22:00") String dndStart,
			@Schema(example = "07:00") String dndEnd, @Schema(example = "Asia/Ho_Chi_Minh") String timeZone,
			boolean marketingConsentGranted, long version) {
	}

	@Transactional(readOnly = true)
	public PreferencesView get(UUID userId) {
		NotificationPreferences current = preferences.find(userId)
			.orElseGet(() -> NotificationPreferences.defaults(userId));
		return viewOf(current, marketingConsent.allowsPushMarketing(userId));
	}

	/**
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException
	 * {@code VALIDATION_FAILED} for SYSTEM switched off, MARKETING without consent, an
	 * empty quiet window or an unknown time zone
	 * @throws ConflictException {@code CONCURRENT_MODIFICATION} when
	 * {@code expectedVersion} is not the stored one
	 */
	@Transactional
	public PreferencesView update(UUID userId, Change change, long expectedVersion) {
		preferences.createDefaultsIfAbsent(userId, clock.instant());
		NotificationPreferences current = preferences.find(userId).orElseThrow();
		boolean consent = marketingConsent.allowsPushMarketing(userId);
		NotificationPreferences updated = current.apply(change, consent);
		NotificationPreferences saved = preferences.saveIfVersion(updated, expectedVersion, clock.instant())
			.orElseThrow(() -> new ConflictException(ErrorCodes.CONCURRENT_MODIFICATION,
					"The preferences changed since you loaded them."));
		return viewOf(saved, consent);
	}

	private static PreferencesView viewOf(NotificationPreferences stored, boolean consent) {
		return new PreferencesView(stored.chatPush(), stored.tradePush(), stored.keywordAlertPush(),
				stored.marketingPush(), true, stored.dndEnabled(), stored.dndStart().toString(),
				stored.dndEnd().toString(), stored.timeZone().getId(), consent, stored.version());
	}

}
