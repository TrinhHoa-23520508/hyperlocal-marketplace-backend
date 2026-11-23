package vn.edu.uit.nearby.nearby_backend.chat.web;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import vn.edu.uit.nearby.nearby_backend.chat.application.ConversationQueries;
import vn.edu.uit.nearby.nearby_backend.chat.application.ConversationQueries.MessageWindow;
import vn.edu.uit.nearby.nearby_backend.chat.application.ConversationQueries.SyncResult;
import vn.edu.uit.nearby.nearby_backend.chat.application.ConversationService;
import vn.edu.uit.nearby.nearby_backend.chat.application.ConversationView;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Request;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Sent;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageView;
import vn.edu.uit.nearby.nearby_backend.chat.application.ReadService;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository.Opened;
import vn.edu.uit.nearby.nearby_backend.chat.web.dto.MarkReadRequest;
import vn.edu.uit.nearby.nearby_backend.chat.web.dto.OpenConversationRequest;
import vn.edu.uit.nearby.nearby_backend.chat.web.dto.SendMessageRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;

@Tag(name = "Chat",
		description = "1-1 conversations about a listing (FR-CH-01..07, FR-CH-11; A10, A17, A18). REST is the source of "
				+ "truth; the same send and read also work over STOMP, which only pushes")
@RestController
@RequestMapping(ApiPaths.CONVERSATIONS_BASE)
public class ConversationController {

	private final ConversationService conversations;

	private final ConversationQueries queries;

	private final MessageService messages;

	private final ReadService reads;

	public ConversationController(ConversationService conversations, ConversationQueries queries,
			MessageService messages, ReadService reads) {
		this.conversations = conversations;
		this.queries = queries;
		this.messages = messages;
		this.reads = reads;
	}

	@Operation(summary = "Open the conversation about a listing: the existing one, or a new one (A10 \"Chat\")",
			description = "Opening twice returns the same id (200). A conversation that already exists is always "
					+ "returned, even if the listing was sold or deleted since.")
	@PostMapping
	@ApiResponse(responseCode = "201", description = "A new conversation was created")
	@ApiResponse(responseCode = "403",
			description = "TRADING_GATE_FAILED (with reasons) | OUT_OF_RANGE | CONTACT_NOT_ALLOWED: neutral text, never says who blocked whom")
	@ApiResponse(responseCode = "404",
			description = "LISTING_NOT_FOUND: unknown, deleted or removed, with no earlier conversation")
	@ApiResponse(responseCode = "409",
			description = "INVALID_LISTING_STATE: your own listing, or one that takes no new chats")
	public ResponseEntity<ConversationView> open(@Valid @RequestBody OpenConversationRequest request) {
		UUID userId = CurrentUser.requireId();
		Opened opened = conversations.open(userId, request.listingId());
		ConversationView view = queries.detail(userId, opened.conversation().id());
		return ResponseEntity.status(opened.created() ? HttpStatus.CREATED : HttpStatus.OK).body(view);
	}

	@Operation(summary = "My conversations, newest activity first (A17)")
	@GetMapping
	@ApiResponse(responseCode = "400", description = "INVALID_CURSOR")
	public CursorPage<ConversationView> list(@RequestParam(required = false) @Nullable String cursor,
			@RequestParam(required = false) @Nullable Integer limit) {
		return queries.list(CurrentUser.requireId(), cursor, limit);
	}

	@Operation(summary = "Everything missed after a reconnect, in id order (FR-CH-07)",
			description = "Send the id of the last message received; ask again with the last id returned while "
					+ "hasMore is true. An unknown checkpoint is INVALID_CURSOR: reload the newest page of each conversation.")
	@GetMapping("/sync")
	@ApiResponse(responseCode = "400", description = "INVALID_CURSOR: the checkpoint is above the newest message")
	public SyncResult sync(@RequestParam long afterMessageId) {
		return queries.sync(CurrentUser.requireId(), afterMessageId);
	}

	@Operation(summary = "The header of the chat screen: listing, counterpart and whether sending is open (A18)")
	@GetMapping("/{conversationId}")
	@ApiResponse(responseCode = "404", description = "CONVERSATION_NOT_FOUND: unknown, or not a member")
	public ConversationView detail(@PathVariable UUID conversationId) {
		return queries.detail(CurrentUser.requireId(), conversationId);
	}

	@Operation(summary = "Messages of one conversation, oldest first",
			description = "No bound: the newest page. before: the page before that id (scroll up). after: the page "
					+ "after that id.")
	@GetMapping("/{conversationId}/messages")
	@ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: both before and after")
	@ApiResponse(responseCode = "404", description = "CONVERSATION_NOT_FOUND")
	public List<MessageView> messages(@PathVariable UUID conversationId,
			@RequestParam(required = false) @Nullable Long before, @RequestParam(required = false) @Nullable Long after,
			@RequestParam(required = false) @Nullable Integer limit) {
		return queries.messages(CurrentUser.requireId(), conversationId, new MessageWindow(before, after, limit));
	}

	@Operation(summary = "Send a message over REST: the fallback when the socket is down (UC-CH-02 A1)",
			description = "201 for a new message; 200 with the stored message when the same clientMsgId was sent before.")
	@PostMapping("/{conversationId}/messages")
	@ApiResponse(responseCode = "201", description = "Stored and delivered")
	@ApiResponse(responseCode = "400",
			description = "VALIDATION_FAILED: empty text, not 1 to 5 images, a point outside Vietnam")
	@ApiResponse(responseCode = "403",
			description = "CONVERSATION_CLOSED: the listing was removed, or a block or sanction; neutral text")
	@ApiResponse(responseCode = "404", description = "CONVERSATION_NOT_FOUND")
	@ApiResponse(responseCode = "422",
			description = "MESSAGE_TOO_LONG | IMAGE_INVALID: not the sender's READY, unused image")
	@ApiResponse(responseCode = "429",
			description = "CHAT_RATE_LIMITED: more than 30 messages a minute; see Retry-After")
	public ResponseEntity<MessageView> send(@PathVariable UUID conversationId,
			@Valid @RequestBody SendMessageRequest request) {
		Sent sent = MessageSending.send(messages,
				new Request(CurrentUser.requireId(), conversationId, request.clientMsgId(), false), request);
		return ResponseEntity.status(sent.created() ? HttpStatus.CREATED : HttpStatus.OK).body(sent.message());
	}

	@Operation(summary = "The reader has seen messages up to this id (FR-CH-06)",
			description = "Opening a conversation sets the reader's unread count to 0 on every device. An older marker is ignored.")
	@PutMapping("/{conversationId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "404", description = "CONVERSATION_NOT_FOUND")
	public void read(@PathVariable UUID conversationId, @Valid @RequestBody MarkReadRequest request) {
		reads.markRead(CurrentUser.requireId(), conversationId, request.lastReadMessageId());
	}

}
