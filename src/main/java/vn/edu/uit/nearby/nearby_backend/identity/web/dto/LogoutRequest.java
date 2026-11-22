package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param fcmToken the push token of this device, so it stops receiving notifications
 */
public record LogoutRequest(@Schema(example = "q3nV7m...") @NotBlank @Size(max = 100) String refreshToken,
		@Schema(description = "Push token of this device; its pushes stop",
				nullable = true) @Nullable @Size(max = 300) String fcmToken) {

}
