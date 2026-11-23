package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences.Change;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.PushDecision.SkipReason;

class PushDecisionTests {

	private static final Instant DAYTIME = Instant.parse("2026-11-23T05:00:00Z");

	private static final Instant NIGHT = Instant.parse("2026-11-23T16:30:00Z");

	private final NotificationPreferences defaults = NotificationPreferences.defaults(UUID.randomUUID());

	@Test
	void aPushIsSentWhenTheCategoryIsOnItIsNotQuietAndADeviceIsRegistered() {
		PushDecision decision = PushDecision.decide(defaults, NotificationCategory.CHAT, false, true, DAYTIME);

		assertThat(decision.shouldSend()).isTrue();
		assertThat(decision.skipReason()).isNull();
	}

	@Test
	@DisplayName("FR-NT-03: TRADE off means SKIPPED, while the in-app row is still created elsewhere")
	void aCategorySwitchedOffIsSkipped() {
		NotificationPreferences tradeOff = defaults.apply(new Change(true, false, true, false, true, false,
				LocalTime.of(22, 0), LocalTime.of(7, 0), "Asia/Ho_Chi_Minh"), false);

		assertThat(PushDecision.decide(tradeOff, NotificationCategory.TRADE, false, true, DAYTIME).skipReason())
			.isEqualTo(SkipReason.CATEGORY_OFF);
		assertThat(PushDecision.decide(tradeOff, NotificationCategory.CHAT, false, true, DAYTIME).shouldSend())
			.isTrue();
	}

	@Test
	@DisplayName("FR-NT-03: inside the quiet hours the push is blocked")
	void quietHoursSkipThePush() {
		NotificationPreferences quiet = defaults.apply(new Change(true, true, true, false, true, true,
				LocalTime.of(22, 0), LocalTime.of(7, 0), "Asia/Ho_Chi_Minh"), false);

		assertThat(PushDecision.decide(quiet, NotificationCategory.CHAT, false, true, NIGHT).skipReason())
			.isEqualTo(SkipReason.QUIET_HOURS);
		assertThat(PushDecision.decide(quiet, NotificationCategory.SYSTEM, false, true, NIGHT).skipReason())
			.isEqualTo(SkipReason.QUIET_HOURS);
		assertThat(PushDecision.decide(quiet, NotificationCategory.CHAT, false, true, DAYTIME).shouldSend()).isTrue();
	}

	@Test
	void noDeviceTokenMeansNothingToSendTo() {
		assertThat(PushDecision.decide(defaults, NotificationCategory.CHAT, false, false, DAYTIME).skipReason())
			.isEqualTo(SkipReason.NO_DEVICE_TOKEN);
	}

	@Test
	@DisplayName("FR-CH-09 (R2): a muted conversation sends no push; the check comes first")
	void aMutedConversationIsSkipped() {
		assertThat(PushDecision.decide(defaults, NotificationCategory.CHAT, true, false, NIGHT).skipReason())
			.isEqualTo(SkipReason.CONVERSATION_MUTED);
	}

	@Test
	void marketingIsOffByDefault() {
		assertThat(PushDecision.decide(defaults, NotificationCategory.MARKETING, false, true, DAYTIME).skipReason())
			.isEqualTo(SkipReason.CATEGORY_OFF);
	}

}
