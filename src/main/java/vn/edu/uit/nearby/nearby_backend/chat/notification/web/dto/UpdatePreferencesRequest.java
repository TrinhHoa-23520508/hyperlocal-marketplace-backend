package vn.edu.uit.nearby.nearby_backend.chat.notification.web.dto;

import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationPreferences.Change;

@Schema(description = "Everything on A30; a PUT replaces all of it (FR-NT-03). SYSTEM cannot be turned off")
public record UpdatePreferencesRequest(@NotNull Boolean chatPush, @NotNull Boolean tradePush,
		@NotNull Boolean keywordAlertPush,
		@Schema(description = "true only with the PUSH_MARKETING consent") @NotNull Boolean marketingPush,
		@Schema(description = "Must be true: SYSTEM and security notices cannot be turned off") @NotNull Boolean systemPush,
		@NotNull Boolean dndEnabled, @Schema(example = "22:00") @NotNull LocalTime dndStart,
		@Schema(example = "07:00",
				description = "May be earlier than the start: the window crosses midnight") @NotNull LocalTime dndEnd,
		@Schema(example = "Asia/Ho_Chi_Minh") @NotBlank String timeZone,
		@Schema(description = "The version read with the preferences; a stale one is 409") @NotNull @PositiveOrZero Long version) {

	public Change toChange() {
		return new Change(chatPush, tradePush, keywordAlertPush, marketingPush, systemPush, dndEnabled, dndStart,
				dndEnd, timeZone);
	}

}
