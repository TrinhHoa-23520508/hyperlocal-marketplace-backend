package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param identifier the phone number, or an email address verified on the account
 */
public record ForgotPasswordRequest(@Schema(description = "Phone number (SMS code) or a verified email (reset link)",
		example = "0912345678") @NotBlank @Size(max = 254) String identifier) {

}
