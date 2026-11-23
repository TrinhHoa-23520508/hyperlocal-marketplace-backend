package vn.edu.uit.nearby.nearby_backend.chat.web;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageAck;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Request;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Sent;
import vn.edu.uit.nearby.nearby_backend.chat.application.ReadService;
import vn.edu.uit.nearby.nearby_backend.chat.web.dto.MarkReadRequest;
import vn.edu.uit.nearby.nearby_backend.chat.web.dto.SendMessageRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.WebSocketDestinations;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ProblemDetails;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;

/**
 * The STOMP side of chat (FR-CH-02, FR-CH-06): {@code SEND
 * /app/conversations/{id}/messages} and {@code SEND /app/conversations/{id}/read}. They
 * run the same services as the REST endpoints, so the rules and the error codes are
 * identical. The sender is acknowledged on {@code /user/queue/acks} and the recipient
 * receives the message on {@code /user/queue/messages}; a failure goes to
 * {@code /user/queue/errors} as the RFC 9457 body of REST plus the {@code clientMsgId} of
 * the message (UC-CH-02 E1..E4). The user comes from the JWT checked when the session
 * connected (UC-CH-02 E6: an expired token is refused at CONNECT).
 */
@Controller
public class ChatStompController {

	private static final Logger log = LoggerFactory.getLogger(ChatStompController.class);

	private final MessageService messages;

	private final ReadService reads;

	private final SimpMessagingTemplate template;

	private final JsonMapper jsonMapper;

	public ChatStompController(MessageService messages, ReadService reads, SimpMessagingTemplate template,
			JsonMapper jsonMapper) {
		this.messages = messages;
		this.reads = reads;
		this.template = template;
		this.jsonMapper = jsonMapper;
	}

	@MessageMapping("/conversations/{conversationId}/messages")
	public void send(@DestinationVariable UUID conversationId, @Validated @Payload SendMessageRequest payload,
			Principal principal) {
		UUID userId = UUID.fromString(principal.getName());
		Sent sent = MessageSending.send(messages, new Request(userId, conversationId, payload.clientMsgId(), true),
				payload);
		if (!sent.created()) {
			// A repeat stores nothing, so nothing is acknowledged after commit: answer
			// now.
			template.convertAndSendToUser(principal.getName(), WebSocketDestinations.QUEUE_ACKS,
					new MessageAck(payload.clientMsgId(), sent.message().id(), sent.message().createdAt()));
		}
	}

	@MessageMapping("/conversations/{conversationId}/read")
	public void read(@DestinationVariable UUID conversationId, @Validated @Payload MarkReadRequest payload,
			Principal principal) {
		reads.markRead(UUID.fromString(principal.getName()), conversationId, payload.lastReadMessageId());
	}

	@MessageExceptionHandler
	@SendToUser(destinations = WebSocketDestinations.QUEUE_ERRORS, broadcast = false)
	public Map<String, Object> onError(Exception ex, Message<?> message) {
		ProblemDetail problem = switch (ex) {
			case BusinessException business -> ProblemDetails.of(business);
			case MethodArgumentNotValidException invalid -> invalidPayload(invalid);
			default -> {
				log.error("Unhandled STOMP error", ex);
				yield ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
						"An unexpected error occurred.");
			}
		};
		Map<String, Object> body = bodyOf(problem);
		if (ex instanceof RateLimitExceededException limited) {
			limited.getRetryAfter().ifPresent(wait -> body.put("retryAfterSeconds", Math.max(1, wait.toSeconds())));
		}
		String clientMsgId = clientMsgIdOf(message);
		if (clientMsgId != null) {
			body.put("clientMsgId", clientMsgId);
		}
		return body;
	}

	private static ProblemDetail invalidPayload(MethodArgumentNotValidException ex) {
		ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
				"Request validation failed.");
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
			.toList();
		problem.setProperty("errors", errors);
		return problem;
	}

	private static Map<String, Object> bodyOf(ProblemDetail problem) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", String.valueOf(problem.getType()));
		body.put("title", problem.getTitle());
		body.put("status", problem.getStatus());
		body.put("detail", problem.getDetail());
		if (problem.getProperties() != null) {
			body.putAll(problem.getProperties());
		}
		return body;
	}

	/**
	 * The app matches an error to the message it sent by its {@code clientMsgId}.
	 */
	private @Nullable String clientMsgIdOf(Message<?> message) {
		try {
			Object payload = message.getPayload();
			String id = payload instanceof byte[] bytes ? jsonMapper.readTree(bytes).path("clientMsgId").asString("")
					: "";
			return id.isEmpty() ? null : id;
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

}
