package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.chat.application.MessageView.ImageView;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageView.LocationView;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageView.SystemView;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageType;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;

/**
 * Turns stored messages into what the app receives. Image keys become short-lived signed
 * links, so a photo can only be fetched by someone who can read the conversation
 * (FR-CH-04, BR-05, NFR-31).
 */
@Component
public class MessageViews {

	private final ImageStorage storage;

	MessageViews(ImageStorage storage) {
		this.storage = storage;
	}

	public List<MessageView> of(Collection<StoredMessage> messages) {
		return messages.stream().map(this::of).toList();
	}

	public MessageView of(StoredMessage message) {
		List<ImageView> images = message.type() == MessageType.IMAGE ? imagesOf(message.payload()) : null;
		LocationView location = message.type() == MessageType.LOCATION ? locationOf(message.payload()) : null;
		SystemView system = message.type() == MessageType.SYSTEM ? systemOf(message.payload()) : null;
		return new MessageView(message.id(), message.conversationId(), message.senderId(), message.type().name(),
				message.body(), images, location, system, message.clientMsgId(), message.createdAt());
	}

	private List<ImageView> imagesOf(Object payload) {
		return ((List<?>) payload).stream().map(entry -> {
			Map<?, ?> image = (Map<?, ?>) entry;
			return new ImageView(storage.presignDownload((String) image.get("key")).url(),
					storage.presignDownload((String) image.get("thumbKey")).url(), ((Number) image.get("w")).intValue(),
					((Number) image.get("h")).intValue());
		}).toList();
	}

	private static LocationView locationOf(Object payload) {
		Map<?, ?> point = (Map<?, ?>) payload;
		return new LocationView(((Number) point.get("lat")).doubleValue(), ((Number) point.get("lng")).doubleValue(),
				(String) point.get("placeName"));
	}

	@SuppressWarnings("unchecked")
	private static SystemView systemOf(Object payload) {
		Map<?, ?> note = (Map<?, ?>) payload;
		return new SystemView((String) note.get("kind"), (Map<String, Object>) note.get("facts"));
	}

}
