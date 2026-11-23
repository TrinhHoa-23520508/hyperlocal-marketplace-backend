package vn.edu.uit.nearby.nearby_backend.chat.notification.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.BadgeService;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.BadgeService.BadgeCounts;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PreferencesService;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PreferencesService.PreferencesView;
import vn.edu.uit.nearby.nearby_backend.chat.notification.web.dto.UpdatePreferencesRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

@Tag(name = "Notification settings",
		description = "Unread badges (FR-NT-06) and push preferences (FR-NT-03, UC-NT-02; A30)")
@RestController
public class NotificationSettingsController {

	private final BadgeService badges;

	private final PreferencesService preferences;

	public NotificationSettingsController(BadgeService badges, PreferencesService preferences) {
		this.badges = badges;
		this.preferences = preferences;
	}

	@Operation(summary = "The two numbers of the bottom navigation: unread chat messages and unread notifications",
			description = "The same counts are pushed on /user/queue/badges to every session of the user.")
	@GetMapping(ApiPaths.BADGES)
	public BadgeCounts badges() {
		return badges.countsOf(CurrentUser.requireId());
	}

	@Operation(summary = "My push preferences; a user who never saved any gets the defaults (A30)")
	@GetMapping(ApiPaths.NOTIFICATION_PREFERENCES)
	public PreferencesView get() {
		return preferences.get(CurrentUser.requireId());
	}

	@Operation(summary = "Save the push preferences and quiet hours (A30)")
	@PutMapping(ApiPaths.NOTIFICATION_PREFERENCES)
	@ApiResponse(responseCode = "400",
			description = "VALIDATION_FAILED: SYSTEM switched off, MARKETING without the PUSH_MARKETING consent, "
					+ "quiet hours that start and end together, or an unknown time zone")
	@ApiResponse(responseCode = "409",
			description = "CONCURRENT_MODIFICATION: the version is not the stored one; reload")
	public PreferencesView update(@Valid @RequestBody UpdatePreferencesRequest request) {
		return preferences.update(CurrentUser.requireId(), request.toChange(), request.version());
	}

}
