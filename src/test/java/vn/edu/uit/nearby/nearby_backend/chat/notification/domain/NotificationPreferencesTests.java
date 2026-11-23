package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences.Change;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

class NotificationPreferencesTests {

	private static final UUID USER = UUID.randomUUID();

	private final NotificationPreferences defaults = NotificationPreferences.defaults(USER);

	@Test
	@DisplayName("data dictionary defaults: chat and trade on, marketing off, quiet hours 22:00-07:00 off")
	void defaultsFollowTheDictionary() {
		assertThat(defaults.chatPush()).isTrue();
		assertThat(defaults.tradePush()).isTrue();
		assertThat(defaults.keywordAlertPush()).isTrue();
		assertThat(defaults.marketingPush()).isFalse();
		assertThat(defaults.dndEnabled()).isFalse();
		assertThat(defaults.dndStart()).isEqualTo(LocalTime.of(22, 0));
		assertThat(defaults.dndEnd()).isEqualTo(LocalTime.of(7, 0));
		assertThat(defaults.timeZone()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
	}

	@Nested
	@DisplayName("FR-NT-03: switches")
	class Switches {

		@Test
		void systemCanNeverBeSwitchedOff() {
			NotificationPreferences allOff = defaults.apply(change(false, false, false, false, true), false);

			for (NotificationCategory category : NotificationCategory.values()) {
				assertThat(allOff.pushEnabledFor(category)).isEqualTo(category == NotificationCategory.SYSTEM);
			}
		}

		@Test
		@DisplayName("turning SYSTEM off: 400")
		void turningSystemOffIsInvalid() {
			assertThatThrownBy(() -> defaults.apply(change(true, true, true, false, false), false))
				.isInstanceOf(InvalidRequestException.class)
				.extracting(error -> ((BusinessException) error).getCode())
				.isEqualTo(ErrorCodes.VALIDATION_FAILED);
		}

		@Test
		@DisplayName("MARKETING can be switched on only with the PUSH_MARKETING consent")
		void marketingNeedsConsent() {
			Change marketingOn = change(true, true, true, true, true);

			assertThatThrownBy(() -> defaults.apply(marketingOn, false)).isInstanceOf(InvalidRequestException.class);
			assertThat(defaults.apply(marketingOn, true).pushEnabledFor(NotificationCategory.MARKETING)).isTrue();
		}

		@Test
		void switchingMarketingOffNeedsNoConsent() {
			assertThat(defaults.apply(change(true, true, true, false, true), false).marketingPush()).isFalse();
		}

		@Test
		void tradeOffTurnsOnlyTradeOff() {
			NotificationPreferences prefs = defaults.apply(change(true, false, true, false, true), false);

			assertThat(prefs.pushEnabledFor(NotificationCategory.TRADE)).isFalse();
			assertThat(prefs.pushEnabledFor(NotificationCategory.CHAT)).isTrue();
		}

		@Test
		void theVersionIsKeptForTheOptimisticLock() {
			NotificationPreferences stored = new NotificationPreferences(USER, true, true, true, false, false,
					LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("Asia/Ho_Chi_Minh"), 4);

			assertThat(stored.apply(change(true, true, true, false, true), false).version()).isEqualTo(4);
		}

		private Change change(boolean chat, boolean trade, boolean keyword, boolean marketing, boolean system) {
			return new Change(chat, trade, keyword, marketing, system, false, LocalTime.of(22, 0), LocalTime.of(7, 0),
					"Asia/Ho_Chi_Minh");
		}

	}

	@Nested
	@DisplayName("FR-NT-03: quiet hours")
	class QuietHours {

		private final NotificationPreferences crossingMidnight = defaults.apply(new Change(true, true, true, false,
				true, true, LocalTime.of(22, 0), LocalTime.of(7, 0), "Asia/Ho_Chi_Minh"), false);

		@ParameterizedTest(name = "{0} UTC = {1} in Ho Chi Minh -> quiet {2}")
		@CsvSource({ "2026-11-23T15:00:00Z, 22:00, true", "2026-11-23T14:59:00Z, 21:59, false",
				"2026-11-23T16:30:00Z, 23:30, true", "2026-11-23T23:59:00Z, 06:59, true",
				"2026-11-24T00:00:00Z, 07:00, false", "2026-11-24T05:00:00Z, 12:00, false" })
		@DisplayName("22:00-07:00 crosses midnight: 23:30 and 06:59 are quiet, 07:00 is not")
		void windowCrossingMidnight(String instant, String local, boolean quiet) {
			assertThat(crossingMidnight.isQuietAt(Instant.parse(instant))).isEqualTo(quiet);
		}

		@Test
		void aWindowWithinOneDayIncludesStartAndExcludesEnd() {
			NotificationPreferences lunch = defaults.apply(new Change(true, true, true, false, true, true,
					LocalTime.of(12, 0), LocalTime.of(13, 0), "Asia/Ho_Chi_Minh"), false);

			assertThat(lunch.isQuietAt(Instant.parse("2026-11-23T05:00:00Z"))).isTrue();
			assertThat(lunch.isQuietAt(Instant.parse("2026-11-23T05:59:00Z"))).isTrue();
			assertThat(lunch.isQuietAt(Instant.parse("2026-11-23T06:00:00Z"))).isFalse();
			assertThat(lunch.isQuietAt(Instant.parse("2026-11-23T04:59:00Z"))).isFalse();
		}

		@Test
		void theWindowFollowsTheUsersTimeZone() {
			NotificationPreferences tokyo = defaults.apply(new Change(true, true, true, false, true, true,
					LocalTime.of(22, 0), LocalTime.of(7, 0), "Asia/Tokyo"), false);

			assertThat(tokyo.isQuietAt(Instant.parse("2026-11-23T13:00:00Z"))).isTrue();
			assertThat(tokyo.isQuietAt(Instant.parse("2026-11-23T15:00:00Z"))).isTrue();
			assertThat(tokyo.isQuietAt(Instant.parse("2026-11-23T03:00:00Z"))).isFalse();
		}

		@Test
		void disabledQuietHoursNeverBlock() {
			assertThat(defaults.isQuietAt(Instant.parse("2026-11-23T16:30:00Z"))).isFalse();
		}

		@Test
		void aWindowThatStartsAndEndsTogetherIsInvalid() {
			Change empty = new Change(true, true, true, false, true, true, LocalTime.of(22, 0), LocalTime.of(22, 0),
					"Asia/Ho_Chi_Minh");

			assertThatThrownBy(() -> defaults.apply(empty, false)).isInstanceOf(InvalidRequestException.class);
		}

		@Test
		void anUnknownTimeZoneIsInvalid() {
			Change unknown = new Change(true, true, true, false, true, true, LocalTime.of(22, 0), LocalTime.of(7, 0),
					"Mars/Olympus");

			assertThatThrownBy(() -> defaults.apply(unknown, false)).isInstanceOf(InvalidRequestException.class);
		}

	}

}
