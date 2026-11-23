package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationTypeTests {

	private static final Pattern DEEP_LINK = Pattern.compile("app://[a-z]+(/[A-Za-z0-9-]+)?");

	@ParameterizedTest
	@EnumSource(NotificationType.class)
	@DisplayName("FR-NT-01: every type renders a title, a body and a working deep link within the column sizes")
	void everyTypeRenders(NotificationType type) {
		NotificationContent content = type.render(type.screen().needsId() ? "6f1c2d9e-3a54" : null, argumentsFor(type));

		assertThat(content.title()).isNotBlank().hasSizeLessThanOrEqualTo(120);
		assertThat(content.body()).isNotBlank().hasSizeLessThanOrEqualTo(300);
		assertThat(content.deepLink().asUri()).matches(DEEP_LINK);
	}

	@Test
	void theDeepLinkTakesTheDictionaryForm() {
		NotificationContent content = NotificationType.MESSAGE_RECEIVED.render("c-1",
				Map.of("sender", "An", "preview", "Còn hàng không bạn?"));

		assertThat(content.deepLink().asUri()).isEqualTo("app://conversations/c-1");
		assertThat(content.title()).isEqualTo("An");
		assertThat(content.body()).isEqualTo("Còn hàng không bạn?");
	}

	@Test
	void screensWithoutAnIdLinkToTheirTab() {
		assertThat(NotificationType.WELCOME.render(null, Map.of()).deepLink().asUri()).isEqualTo("app://home");
		assertThat(NotificationType.SANCTION_LIFTED.render(null, Map.of()).deepLink().asUri())
			.isEqualTo("app://account");
	}

	@Test
	void aLongBodyIsCutWithAnEllipsisAtThreeHundredCharacters() {
		NotificationContent content = NotificationType.MESSAGE_RECEIVED.render("c-1",
				Map.of("sender", "An", "preview", "x".repeat(500)));

		assertThat(content.body()).hasSize(300).endsWith("…");
	}

	@Test
	void aLongTitleIsCutAtOneHundredTwentyCharacters() {
		NotificationContent content = NotificationType.MESSAGE_RECEIVED.render("c-1",
				Map.of("sender", "n".repeat(200), "preview", "hi"));

		assertThat(content.title()).hasSize(120).endsWith("…");
	}

	@Test
	void argumentsWithSpecialCharactersAreInsertedAsTheyAre() {
		NotificationContent content = NotificationType.MESSAGE_RECEIVED.render("c-1",
				Map.of("sender", "An", "preview", "giá $100 \\ {preview}"));

		assertThat(content.body()).isEqualTo("giá $100 \\ {preview}");
	}

	@Test
	void aMissingArgumentIsAProducerBug() {
		assertThatThrownBy(() -> NotificationType.MESSAGE_RECEIVED.render("c-1", Map.of("sender", "An")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("preview");
	}

	@Test
	void aTargetThatDoesNotFitTheScreenIsAProducerBug() {
		assertThatThrownBy(() -> new DeepLink(DeepLink.Screen.CONVERSATIONS, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DeepLink(DeepLink.Screen.HOME, "x")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("plan reading R5: the category of each producer that exists now")
	void categoriesOfTheTypesBuiltNow() {
		assertThat(NotificationType.MESSAGE_RECEIVED.category()).isEqualTo(NotificationCategory.CHAT);
		assertThat(NotificationType.OFFER_RECEIVED.category()).isEqualTo(NotificationCategory.TRADE);
		assertThat(NotificationType.WELCOME.category()).isEqualTo(NotificationCategory.SYSTEM);
		assertThat(NotificationType.AREA_EXPIRING.category()).isEqualTo(NotificationCategory.SYSTEM);
		assertThat(NotificationType.SANCTION_BANNED.category()).isEqualTo(NotificationCategory.SYSTEM);
	}

	private static Map<String, String> argumentsFor(NotificationType type) {
		Map<String, String> arguments = new HashMap<>();
		for (String name : new String[] { "sender", "preview", "expiresOn", "until", "buyer", "seller", "price",
				"listing", "time", "amount", "partner", "outcome" }) {
			arguments.put(name, name + "-value");
		}
		return arguments;
	}

}
