package vn.edu.uit.nearby.nearby_backend.chat.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.nearby.nearby_backend.areaverification.InteractionRange;
import vn.edu.uit.nearby.nearby_backend.chat.application.ChatUpstreamFacades;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AccountStanding;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.listing.ListingCounters;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;
import vn.edu.uit.nearby.nearby_backend.listing.ListingSnapshot;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.Roles;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.support.InMemoryImageStorage;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;
import vn.edu.uit.nearby.nearby_backend.support.TestImages;

/**
 * The chat REST API: status codes, RFC 9457 bodies and the screens A10, A17 and A18
 * (FR-CH-01..07, FR-CH-11). The facades of other modules are mocks.
 */
@IntegrationTest
@Import(ChatUpstreamFacades.class)
class ChatApiIntegrationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	TokenService tokens;

	@Autowired
	ListingLookup listings;

	@Autowired
	ListingCounters listingCounters;

	@Autowired
	UserBlocks blocks;

	@Autowired
	AccountStanding standing;

	@Autowired
	TradingGate tradingGate;

	@Autowired
	InteractionRange interactionRange;

	@Autowired
	ChatImageRepository chatImages;

	@Autowired
	InMemoryImageStorage storage;

	private final UUID buyer = UUID.randomUUID();

	private final UUID seller = UUID.randomUUID();

	private final UUID listingId = UUID.randomUUID();

	private final UUID area = UUID.randomUUID();

	@BeforeEach
	void givenAnActiveListing() {
		reset(listings, listingCounters, blocks, standing, tradingGate, interactionRange);
		givenListingStatus("ACTIVE", seller);
		when(standing.isActiveWithVerifiedPhone(any())).thenReturn(true);
		when(interactionRange.allowsNewContacts(area)).thenReturn(true);
	}

	private void givenListingStatus(String status, UUID owner) {
		ListingSnapshot snapshot = new ListingSnapshot(listingId, owner, area, "Ghế gỗ", status, 150_000, true,
				new GeoPoint(10.77, 106.70), null, null);
		when(listings.find(listingId)).thenReturn(Optional.of(snapshot));
		when(listings.findAll(any())).thenReturn(Map.of(listingId, snapshot));
	}

	private MockHttpServletRequestBuilder as(UUID user, MockHttpServletRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issueAccessToken(user, Roles.USER).value());
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private UUID openConversation() throws Exception {
		String body = send(as(buyer, post("/api/v1/conversations")), Map.of("listingId", listingId)).andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(json.readTree(body).path("id").asString());
	}

	private ResultActions text(UUID conversation, UUID sender, String body) throws Exception {
		return send(as(sender, post("/api/v1/conversations/{id}/messages", conversation)),
				Map.of("clientMsgId", UUID.randomUUID(), "type", "TEXT", "body", body));
	}

	@Nested
	@DisplayName("POST /conversations (UC-CH-01, A10)")
	class Opening {

		@Test
		@DisplayName("FR-CH-01: 201 the first time, 200 and the same id after, with the header of A18")
		void opensOnceAndReturnsTheSameConversation() throws Exception {
			ResultActions first = send(as(buyer, post("/api/v1/conversations")), Map.of("listingId", listingId));
			first.andExpect(status().isCreated())
				.andExpect(jsonPath("$.listing.title").value("Ghế gỗ"))
				.andExpect(jsonPath("$.sendOpen").value(true))
				.andExpect(jsonPath("$.unreadCount").value(0));
			String id = json.readTree(first.andReturn().getResponse().getContentAsString()).path("id").asString();

			send(as(buyer, post("/api/v1/conversations")), Map.of("listingId", listingId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id));
		}

		@Test
		void aGuestIsUnauthorized() throws Exception {
			send(post("/api/v1/conversations"), Map.of("listingId", listingId)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHENTICATED));
		}

		@Test
		void aMissingListingIdIsABadRequest() throws Exception {
			send(as(buyer, post("/api/v1/conversations")), Map.of()).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
				.andExpect(jsonPath("$.errors[0].field").value("listingId"));
		}

		@Test
		@DisplayName("a failed trading gate is 403 TRADING_GATE_FAILED with the reasons")
		void aFailedGateIsForbiddenWithReasons() throws Exception {
			doThrow(new OperationNotAllowedException(ErrorCodes.TRADING_GATE_FAILED, "Complete the missing steps.",
					Map.of("reasons", java.util.List.of("NO_VALID_AREA"))))
				.when(tradingGate)
				.requireOpen(buyer);

			send(as(buyer, post("/api/v1/conversations")), Map.of("listingId", listingId))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCodes.TRADING_GATE_FAILED))
				.andExpect(jsonPath("$.reasons[0]").value("NO_VALID_AREA"));
		}

		@Test
		void aBlockIsForbiddenInNeutralWords() throws Exception {
			when(blocks.isBlockedEitherWay(buyer, seller)).thenReturn(true);

			send(as(buyer, post("/api/v1/conversations")), Map.of("listingId", listingId))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCodes.CONTACT_NOT_ALLOWED));
		}

		@Test
		void yourOwnListingIsAConflict() throws Exception {
			givenListingStatus("ACTIVE", buyer);

			send(as(buyer, post("/api/v1/conversations")), Map.of("listingId", listingId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_LISTING_STATE));
		}

		@Test
		void anUnknownListingIsNotFound() throws Exception {
			send(as(buyer, post("/api/v1/conversations")), Map.of("listingId", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCodes.LISTING_NOT_FOUND));
		}

	}

	@Nested
	@DisplayName("messages (UC-CH-02..04, A18)")
	class Messages {

		@Test
		@DisplayName("FR-CH-02: 201 for a new message, 200 with the same message for a repeated clientMsgId")
		void aRepeatReturns200() throws Exception {
			UUID conversation = openConversation();
			Map<String, Object> body = Map.of("clientMsgId", UUID.randomUUID(), "type", "TEXT", "body",
					"Còn hàng không bạn?");

			ResultActions first = send(as(buyer, post("/api/v1/conversations/{id}/messages", conversation)), body);
			first.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("TEXT"))
				.andExpect(jsonPath("$.body").value("Còn hàng không bạn?"))
				.andExpect(jsonPath("$.senderId").value(buyer.toString()));
			String id = json.readTree(first.andReturn().getResponse().getContentAsString()).path("id").asString();

			send(as(buyer, post("/api/v1/conversations/{id}/messages", conversation)), body).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(Long.parseLong(id)));
			mvc.perform(as(buyer, get("/api/v1/conversations/{id}/messages", conversation)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
		}

		@Test
		@DisplayName("FR-CH-03: empty text is 400, 1 001 characters is 422 MESSAGE_TOO_LONG")
		void textLimits() throws Exception {
			UUID conversation = openConversation();

			text(conversation, buyer, "   ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED));
			text(conversation, buyer, "a".repeat(1_001)).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value(ErrorCodes.MESSAGE_TOO_LONG));
			text(conversation, buyer, "a".repeat(1_000)).andExpect(status().isCreated());
		}

		@Test
		@DisplayName("FR-CH-03: the 31st message in a minute is 429 CHAT_RATE_LIMITED with Retry-After")
		void rateLimit() throws Exception {
			UUID conversation = openConversation();
			for (int i = 0; i < 30; i++) {
				text(conversation, buyer, "tin " + i).andExpect(status().isCreated());
			}

			text(conversation, buyer, "tin 31").andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value(ErrorCodes.CHAT_RATE_LIMITED))
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		}

		@Test
		@DisplayName("FR-CH-11: after REMOVED sending is 403 CONVERSATION_CLOSED, and reading still works")
		void aRemovedListingClosesSending() throws Exception {
			UUID conversation = openConversation();
			text(conversation, buyer, "xin chào").andExpect(status().isCreated());
			givenListingStatus("REMOVED", seller);

			text(conversation, seller, "chào bạn").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCodes.CONVERSATION_CLOSED));
			mvc.perform(as(seller, get("/api/v1/conversations/{id}", conversation)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sendOpen").value(false))
				.andExpect(jsonPath("$.closedNotice").exists());
			mvc.perform(as(seller, get("/api/v1/conversations/{id}/messages", conversation)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
		}

		@Test
		@DisplayName("FR-CH-02 E4: a stranger gets 404 CONVERSATION_NOT_FOUND on every endpoint")
		void aStrangerGetsNotFound() throws Exception {
			UUID conversation = openConversation();
			UUID stranger = UUID.randomUUID();

			text(conversation, stranger, "xin chào").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCodes.CONVERSATION_NOT_FOUND));
			mvc.perform(as(stranger, get("/api/v1/conversations/{id}", conversation))).andExpect(status().isNotFound());
			mvc.perform(as(stranger, get("/api/v1/conversations/{id}/messages", conversation)))
				.andExpect(status().isNotFound());
			send(as(stranger, put("/api/v1/conversations/{id}/read", conversation)), Map.of("lastReadMessageId", 1))
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("FR-CH-05: a meeting point is shared; outside Vietnam it is 400")
		void meetingPoints() throws Exception {
			UUID conversation = openConversation();

			send(as(buyer, post("/api/v1/conversations/{id}/messages", conversation)),
					Map.of("clientMsgId", UUID.randomUUID(), "type", "LOCATION", "point",
							Map.of("latitude", 10.7769, "longitude", 106.7009, "placeName", "Công viên 23/9")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.location.placeName").value("Công viên 23/9"))
				.andExpect(jsonPath("$.location.latitude").value(10.7769));
			send(as(buyer, post("/api/v1/conversations/{id}/messages", conversation)),
					Map.of("clientMsgId", UUID.randomUUID(), "type", "LOCATION", "point",
							Map.of("latitude", 48.85, "longitude", 2.35, "placeName", "Paris")))
				.andExpect(status().isBadRequest());
			send(as(buyer, post("/api/v1/conversations/{id}/messages", conversation)),
					Map.of("clientMsgId", UUID.randomUUID(), "type", "LOCATION"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("point"));
		}

		@Test
		void aMissingTypeIsABadRequest() throws Exception {
			UUID conversation = openConversation();

			send(as(buyer, post("/api/v1/conversations/{id}/messages", conversation)),
					Map.of("clientMsgId", UUID.randomUUID(), "body", "hi"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("type"));
		}

		@Test
		@DisplayName("FR-CH-07: paging before and after, and both together are refused")
		void paging() throws Exception {
			UUID conversation = openConversation();
			for (int i = 0; i < 3; i++) {
				text(conversation, buyer, "m" + i);
			}

			mvc.perform(as(buyer, get("/api/v1/conversations/{id}/messages", conversation).param("limit", "2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[1].body").value("m2"));
			mvc.perform(as(buyer,
					get("/api/v1/conversations/{id}/messages", conversation).param("before", "1").param("after", "1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED));
		}

	}

	@Nested
	@DisplayName("read, list and sync (UC-CH-05, FR-CH-06, FR-CH-07)")
	class ReadingListingAndSyncing {

		@Test
		@DisplayName("FR-CH-06: the list shows the unread count; PUT read clears it with 204")
		void readingClearsTheUnreadCount() throws Exception {
			UUID conversation = openConversation();
			String sent = text(conversation, buyer, "Còn hàng không bạn?").andReturn()
				.getResponse()
				.getContentAsString();
			long messageId = Long.parseLong(json.readTree(sent).path("id").asString());

			mvc.perform(as(seller, get("/api/v1/conversations")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].unreadCount").value(1))
				.andExpect(jsonPath("$.items[0].lastMessagePreview").value("Còn hàng không bạn?"))
				.andExpect(jsonPath("$.hasMore").value(false));
			send(as(seller, put("/api/v1/conversations/{id}/read", conversation)),
					Map.of("lastReadMessageId", messageId))
				.andExpect(status().isNoContent());
			mvc.perform(as(seller, get("/api/v1/conversations/{id}", conversation)))
				.andExpect(jsonPath("$.unreadCount").value(0));
			mvc.perform(as(seller, get("/api/v1/users/me/badges")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.chatUnread").value(0));
		}

		@Test
		void aNegativeMarkerIsABadRequest() throws Exception {
			UUID conversation = openConversation();

			send(as(buyer, put("/api/v1/conversations/{id}/read", conversation)), Map.of("lastReadMessageId", -1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("lastReadMessageId"));
		}

		@Test
		void aBrokenListCursorIsRejected() throws Exception {
			mvc.perform(as(buyer, get("/api/v1/conversations").param("cursor", "not-a-cursor")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_CURSOR));
		}

		@Test
		@DisplayName("FR-CH-07: sync returns what was missed, and an unknown checkpoint is INVALID_CURSOR")
		void sync() throws Exception {
			UUID conversation = openConversation();
			String first = text(conversation, buyer, "một").andReturn().getResponse().getContentAsString();
			text(conversation, buyer, "hai");
			long firstId = Long.parseLong(json.readTree(first).path("id").asString());

			mvc.perform(as(seller, get("/api/v1/conversations/sync").param("afterMessageId", String.valueOf(firstId))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages", hasSize(1)))
				.andExpect(jsonPath("$.messages[0].body").value("hai"))
				.andExpect(jsonPath("$.hasMore").value(false));
			mvc.perform(as(seller, get("/api/v1/conversations/sync").param("afterMessageId", "999999999999")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_CURSOR));
			mvc.perform(as(seller, get("/api/v1/conversations/sync")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED));
		}

	}

	@Nested
	@DisplayName("chat images (UC-CH-03)")
	class Images {

		@Test
		@DisplayName("FR-CH-04: upload URL, complete, poll until READY, then send; the counterpart sees it")
		void imageFlow() throws Exception {
			UUID conversation = openConversation();

			String ticket = send(as(buyer, post("/api/v1/chat-images/upload-url")),
					Map.of("conversationId", conversation, "contentType", "image/jpeg", "sizeBytes", 100_000))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uploadMethod").value("PUT"))
				.andReturn()
				.getResponse()
				.getContentAsString();
			UUID imageId = UUID.fromString(json.readTree(ticket).path("imageId").asString());
			storage.simulateUpload(chatImages.findById(imageId).orElseThrow().originalKey(), "image/jpeg",
					TestImages.jpeg(640, 480));

			mvc.perform(as(buyer, post("/api/v1/chat-images/{id}/complete", imageId))).andExpect(status().isAccepted());
			await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> mvc.perform(as(buyer, get("/api/v1/chat-images/{id}", imageId)))
					.andExpect(jsonPath("$.status").value("READY")));
			mvc.perform(as(seller, get("/api/v1/chat-images/{id}", imageId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCodes.IMAGE_NOT_FOUND));
			send(as(buyer, post("/api/v1/conversations/{id}/messages", conversation)),
					Map.of("clientMsgId", UUID.randomUUID(), "type", "IMAGE", "imageIds", java.util.List.of(imageId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.images", hasSize(1)))
				.andExpect(jsonPath("$.images[0].url").value(containsString("chat-images/" + imageId)));
			mvc.perform(as(seller, get("/api/v1/chat-images/{id}", imageId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").exists());
		}

		@Test
		void aWrongTypeIsABadRequestAndAStrangerGetsNotFound() throws Exception {
			UUID conversation = openConversation();

			send(as(buyer, post("/api/v1/chat-images/upload-url")),
					Map.of("conversationId", conversation, "contentType", "image/gif", "sizeBytes", 1_000))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_IMAGE));
			send(as(UUID.randomUUID(), post("/api/v1/chat-images/upload-url")),
					Map.of("conversationId", conversation, "contentType", "image/jpeg", "sizeBytes", 1_000))
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("an image that is not READY, or not the sender's, is 422 IMAGE_INVALID")
		void anUnusableImageIsRefused() throws Exception {
			UUID conversation = openConversation();

			send(as(buyer, post("/api/v1/conversations/{id}/messages", conversation)),
					Map.of("clientMsgId", UUID.randomUUID(), "type", "IMAGE", "imageIds",
							java.util.List.of(UUID.randomUUID())))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value(ErrorCodes.IMAGE_INVALID));
			send(as(buyer, post("/api/v1/conversations/{id}/messages", conversation)),
					Map.of("clientMsgId", UUID.randomUUID(), "type", "IMAGE"))
				.andExpect(status().isBadRequest());
		}

	}

}
