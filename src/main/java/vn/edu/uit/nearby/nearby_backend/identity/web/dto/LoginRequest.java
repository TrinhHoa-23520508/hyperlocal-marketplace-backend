package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param identifier the phone number, or an email address verified on the account
 */
public record LoginRequest(
		@Schema(description = "Phone number or a verified email",
				example = "0912345678") @NotBlank @Size(max = 254) String identifier,
		@Schema(example = "Secret123") @NotBlank @Size(max = 200) String password) {

}
