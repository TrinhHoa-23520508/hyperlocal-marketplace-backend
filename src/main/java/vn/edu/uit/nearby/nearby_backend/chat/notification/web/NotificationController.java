package vn.edu.uit.nearby.nearby_backend.chat.notification.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.NotificationQueries;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.NotificationQueries.NotificationView;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;

@Tag(name = "Notifications", description = "The in-app notification list (FR-NT-05, UC-NT-01; A29)")
@RestController
@RequestMapping(ApiPaths.NOTIFICATIONS_BASE)
public class NotificationController {

	private final NotificationQueries notifications;

	public NotificationController(NotificationQueries notifications) {
		this.notifications = notifications;
	}

	@Operation(summary = "My notifications, newest first; unread ones have read=false (A29)")
	@GetMapping
	@ApiResponse(responseCode = "400", description = "INVALID_CURSOR")
	public CursorPage<NotificationView> list(@RequestParam(required = false) @Nullable String cursor,
			@RequestParam(required = false) @Nullable Integer limit) {
		return notifications.list(CurrentUser.requireId(), cursor, limit);
	}

	@Operation(summary = "Mark one notification read when the user taps it, then open its deepLink",
			description = "Marking a read notification again changes nothing.")
	@PostMapping("/{notificationId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "404", description = "NOTIFICATION_NOT_FOUND: unknown, or someone else's")
	public void markRead(@PathVariable UUID notificationId) {
		notifications.markRead(CurrentUser.requireId(), notificationId);
	}

	@Operation(summary = "Mark every notification read: the badge becomes 0")
	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markAllRead() {
		notifications.markAllRead(CurrentUser.requireId());
	}

}
