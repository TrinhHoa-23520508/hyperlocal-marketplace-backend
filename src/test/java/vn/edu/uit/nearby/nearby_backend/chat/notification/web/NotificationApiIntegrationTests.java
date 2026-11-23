package vn.edu.uit.nearby.nearby_backend.chat.notification.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationCategory;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.DeviceTokenRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure.NotificationRepository.NewNotification;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.Roles;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * The notification REST API: the list (A29), read state, badges, preferences (A30) and
 * the device token (FR-NT-02, FR-NT-03, FR-NT-05, FR-NT-06).
 */
@IntegrationTest
class NotificationApiIntegrationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	TokenService tokens;

	@Autowired
	NotificationRepository notifications;

	@Autowired
	DeviceTokenRepository devices;

	private final UUID user = UUID.randomUUID();

	private MockHttpServletRequestBuilder as(UUID who, MockHttpServletRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issueAccessToken(who, Roles.USER).value());
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private void givenNotifications(int count) {
		for (int i = 0; i < count; i++) {
			notifications.insertIfAbsent(new NewNotification(UUID.randomUUID(), user, NotificationCategory.TRADE,
					"OFFER_RECEIVED", "Có đề nghị mới " + i, "Một người muốn mua", "app://transactions/t-" + i, null,
					UUID.randomUUID(), "SKIPPED", Instant.now().plusSeconds(i)));
		}
	}

	private Map<String, Object> preferences(long version) {
		return Map.ofEntries(Map.entry("chatPush", true), Map.entry("tradePush", false),
				Map.entry("keywordAlertPush", true), Map.entry("marketingPush", false), Map.entry("systemPush", true),
				Map.entry("dndEnabled", true), Map.entry("dndStart", "23:00"), Map.entry("dndEnd", "06:30"),
				Map.entry("timeZone", "Asia/Ho_Chi_Minh"), Map.entry("version", version));
	}

	@Nested
	@DisplayName("GET /notifications (UC-NT-01, A29)")
	class Listing {

		@Test
		@DisplayName("FR-NT-05: newest first, unread highlighted, a cursor for the next page")
		void listsWithACursor() throws Exception {
			givenNotifications(3);

			String first = mvc.perform(as(user, get("/api/v1/notifications").param("limit", "2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[0].title").value("Có đề nghị mới 2"))
				.andExpect(jsonPath("$.items[0].read").value(false))
				.andExpect(jsonPath("$.items[0].deepLink").value("app://transactions/t-2"))
				.andExpect(jsonPath("$.hasMore").value(true))
				.andReturn()
				.getResponse()
				.getContentAsString();
			String cursor = json.readTree(first).path("nextCursor").asString();

			mvc.perform(as(user, get("/api/v1/notifications").param("cursor", cursor)))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.hasMore").value(false));
		}

		@Test
		void aGuestIsUnauthorized() throws Exception {
			mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
		}

		@Test
		void aBrokenCursorIsRejected() throws Exception {
			mvc.perform(as(user, get("/api/v1/notifications").param("cursor", "x")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_CURSOR));
		}

		@Test
		@DisplayName("FR-NT-05 / FR-NT-06: tapping one lowers the badge; read-all sets it to 0")
		void readStateAndBadges() throws Exception {
			givenNotifications(3);
			String list = mvc.perform(as(user, get("/api/v1/notifications")))
				.andReturn()
				.getResponse()
				.getContentAsString();
			String id = json.readTree(list).path("items").get(0).path("id").asString();

			mvc.perform(as(user, get("/api/v1/users/me/badges")))
				.andExpect(jsonPath("$.notificationUnread").value(3))
				.andExpect(jsonPath("$.chatUnread").value(0));
			mvc.perform(as(user, post("/api/v1/notifications/{id}/read", id))).andExpect(status().isNoContent());
			mvc.perform(as(user, post("/api/v1/notifications/{id}/read", id))).andExpect(status().isNoContent());
			mvc.perform(as(user, get("/api/v1/users/me/badges"))).andExpect(jsonPath("$.notificationUnread").value(2));
			mvc.perform(as(user, post("/api/v1/notifications/read-all"))).andExpect(status().isNoContent());
			mvc.perform(as(user, get("/api/v1/users/me/badges"))).andExpect(jsonPath("$.notificationUnread").value(0));
		}

		@Test
		void someoneElsesNotificationIsNotFound() throws Exception {
			givenNotifications(1);
			String list = mvc.perform(as(user, get("/api/v1/notifications")))
				.andReturn()
				.getResponse()
				.getContentAsString();
			String id = json.readTree(list).path("items").get(0).path("id").asString();

			mvc.perform(as(UUID.randomUUID(), post("/api/v1/notifications/{id}/read", id)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCodes.NOTIFICATION_NOT_FOUND));
		}

	}

	@Nested
	@DisplayName("preferences (UC-NT-02, A30)")
	class Preferences {

		@Test
		@DisplayName("a user who never saved gets the defaults, with SYSTEM on")
		void defaults() throws Exception {
			mvc.perform(as(user, get("/api/v1/users/me/notification-preferences")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.chatPush").value(true))
				.andExpect(jsonPath("$.tradePush").value(true))
				.andExpect(jsonPath("$.marketingPush").value(false))
				.andExpect(jsonPath("$.systemPush").value(true))
				.andExpect(jsonPath("$.dndEnabled").value(false))
				.andExpect(jsonPath("$.dndStart").value("22:00"))
				.andExpect(jsonPath("$.dndEnd").value("07:00"))
				.andExpect(jsonPath("$.version").value(0));
		}

		@Test
		@DisplayName("FR-NT-03: saving stores the switches and quiet hours and bumps the version")
		void save() throws Exception {
			send(as(user, put("/api/v1/users/me/notification-preferences")), preferences(0)).andExpect(status().isOk())
				.andExpect(jsonPath("$.tradePush").value(false))
				.andExpect(jsonPath("$.dndStart").value("23:00"))
				.andExpect(jsonPath("$.dndEnd").value("06:30"))
				.andExpect(jsonPath("$.version").value(1));
			mvc.perform(as(user, get("/api/v1/users/me/notification-preferences")))
				.andExpect(jsonPath("$.tradePush").value(false));
		}

		@Test
		@DisplayName("a stale version is 409 CONCURRENT_MODIFICATION")
		void aStaleVersionConflicts() throws Exception {
			send(as(user, put("/api/v1/users/me/notification-preferences")), preferences(0)).andExpect(status().isOk());

			send(as(user, put("/api/v1/users/me/notification-preferences")), preferences(0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorCodes.CONCURRENT_MODIFICATION));
		}

		@Test
		@DisplayName("FR-NT-03: turning SYSTEM off is 400")
		void systemCannotBeTurnedOff() throws Exception {
			Map<String, Object> body = new java.util.HashMap<>(preferences(0));
			body.put("systemPush", false);

			send(as(user, put("/api/v1/users/me/notification-preferences")), body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
				.andExpect(jsonPath("$.errors[0].field").value("systemPush"));
		}

		@Test
		@DisplayName("FR-NT-03: MARKETING without the PUSH_MARKETING consent is 400")
		void marketingNeedsConsent() throws Exception {
			Map<String, Object> body = new java.util.HashMap<>(preferences(0));
			body.put("marketingPush", true);

			send(as(user, put("/api/v1/users/me/notification-preferences")), body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("marketingPush"));
		}

		@Test
		void invalidQuietHoursAndZonesAreRejected() throws Exception {
			Map<String, Object> sameTime = new java.util.HashMap<>(preferences(0));
			sameTime.put("dndEnd", "23:00");
			Map<String, Object> unknownZone = new java.util.HashMap<>(preferences(0));
			unknownZone.put("timeZone", "Mars/Olympus");

			send(as(user, put("/api/v1/users/me/notification-preferences")), sameTime)
				.andExpect(status().isBadRequest());
			send(as(user, put("/api/v1/users/me/notification-preferences")), unknownZone)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("timeZone"));
		}

		@Test
		void aMissingFieldIsABadRequest() throws Exception {
			Map<String, Object> body = new java.util.HashMap<>(preferences(0));
			body.remove("tradePush");

			send(as(user, put("/api/v1/users/me/notification-preferences")), body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("tradePush"));
		}

	}

	@Nested
	@DisplayName("PUT /devices/{deviceId} (FR-NT-02, ID-W1)")
	class Devices {

		@Test
		@DisplayName("registering stores one token per device; a rotated token replaces the old one")
		void registersAndRotates() throws Exception {
			send(as(user, put("/api/v1/devices/{id}", "device-1")),
					Map.of("fcmToken", "token-a-" + user, "platform", "android", "appVersion", "1.4.2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deviceId").value("device-1"))
				.andExpect(jsonPath("$.platform").value("ANDROID"));
			send(as(user, put("/api/v1/devices/{id}", "device-1")),
					Map.of("fcmToken", "token-b-" + user, "platform", "ANDROID"))
				.andExpect(status().isOk());

			org.assertj.core.api.Assertions.assertThat(devices.tokensOf(user)).containsExactly("token-b-" + user);
		}

		@Test
		void invalidInputIsABadRequest() throws Exception {
			send(as(user, put("/api/v1/devices/{id}", "device-1")), Map.of("fcmToken", " ", "platform", "ANDROID"))
				.andExpect(status().isBadRequest());
			send(as(user, put("/api/v1/devices/{id}", "device-1")), Map.of("fcmToken", "t", "platform", "IOS"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("platform"));
			send(as(user, put("/api/v1/devices/{id}", "d".repeat(65))), Map.of("fcmToken", "t", "platform", "ANDROID"))
				.andExpect(status().isBadRequest());
		}

		@Test
		void aGuestIsUnauthorized() throws Exception {
			send(put("/api/v1/devices/{id}", "device-1"), Map.of("fcmToken", "t", "platform", "ANDROID"))
				.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("deleting forgets the token and is safe to repeat")
		void unregisters() throws Exception {
			send(as(user, put("/api/v1/devices/{id}", "device-1")),
					Map.of("fcmToken", "token-c-" + user, "platform", "ANDROID"))
				.andExpect(status().isOk());

			mvc.perform(as(user, delete("/api/v1/devices/{id}", "device-1"))).andExpect(status().isNoContent());
			mvc.perform(as(user, delete("/api/v1/devices/{id}", "device-1"))).andExpect(status().isNoContent());

			org.assertj.core.api.Assertions.assertThat(devices.tokensOf(user)).isEmpty();
		}

	}

}
