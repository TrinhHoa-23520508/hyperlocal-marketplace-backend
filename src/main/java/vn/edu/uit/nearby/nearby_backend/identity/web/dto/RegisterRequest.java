package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.PolicyAcceptance;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.RegisterCommand;

/**
 * @param termsVersion version of the Terms of Service the user accepted; omitted when not
 * accepted
 * @param privacyVersion version of the Privacy Policy the user accepted; omitted when not
 * accepted
 */
public record RegisterRequest(
		@Schema(description = "Same number the code was sent to; 0912345678, 84912345678 or +84912345678",
				example = "0912345678") @NotBlank @Size(max = 20) String phone,
		@Schema(description = "From POST /auth/register/otp") @NotNull UUID challengeId,
		@Schema(description = "6-digit SMS code",
				example = "123456") @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
		@Schema(description = "At least 8 characters, at most 72 UTF-8 bytes, letters and digits",
				example = "Secret123") @NotBlank @Size(max = 200) String password,
		@Schema(example = "An Nguyen") @NotBlank @Size(min = 2, max = 30) String displayName,
		@Schema(description = "Accepted Terms version; leave out when not accepted", example = "1.0",
				nullable = true) @Nullable @Size(max = 20) String termsVersion,
		@Schema(description = "Accepted Privacy Policy version; leave out when not accepted", example = "1.0",
				nullable = true) @Nullable @Size(max = 20) String privacyVersion,
		@Schema(description = "Optional consent to marketing pushes", example = "false",
				nullable = true) @Nullable Boolean pushMarketing) {

	public RegisterCommand toCommand(String clientIp) {
		return new RegisterCommand(phone, challengeId, otp, password, displayName,
				new PolicyAcceptance(termsVersion, privacyVersion, Boolean.TRUE.equals(pushMarketing)), clientIp);
	}

}
