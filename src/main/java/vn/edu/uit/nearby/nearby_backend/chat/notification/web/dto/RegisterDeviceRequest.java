package vn.edu.uit.nearby.nearby_backend.chat.notification.web.dto;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "The FCM token of this installation (FR-NT-02). Send it after login and whenever FCM rotates it")
public record RegisterDeviceRequest(
		@Schema(description = "Registration token from FirebaseMessaging") @NotBlank String fcmToken,
		@Schema(example = "ANDROID", allowableValues = {
				"ANDROID", "WEB" }) @NotBlank String platform,
		@Schema(example = "1.4.2") @Nullable String appVersion){

}
