package vn.edu.uit.nearby.nearby_backend.chat.web;

import java.util.List;

import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Request;
import vn.edu.uit.nearby.nearby_backend.chat.application.MessageService.Sent;
import vn.edu.uit.nearby.nearby_backend.chat.web.dto.SendMessageRequest;
import vn.edu.uit.nearby.nearby_backend.chat.web.dto.SendMessageRequest.PointRequest;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Turns one {@link SendMessageRequest} into the right send call, so REST and STOMP send
 * the same way and answer the same errors.
 */
final class MessageSending {

	private MessageSending() {
	}

	static Sent send(MessageService messages, Request request, SendMessageRequest body) {
		return switch (body.type()) {
			case TEXT -> messages.sendText(request, body.body());
			case IMAGE -> messages.sendImages(request, body.imageIds() == null ? List.of() : body.imageIds());
			case LOCATION -> {
				PointRequest point = body.point();
				if (point == null) {
					throw InvalidRequestException.invalidField("point", "A LOCATION message needs a point.");
				}
				yield messages.sendMeetingPoint(request, point.latitude(), point.longitude(), point.placeName());
			}
		};
	}

}
