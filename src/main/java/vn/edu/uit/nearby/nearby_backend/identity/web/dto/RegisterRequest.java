package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

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
public record RegisterRequest(@NotBlank @Size(max = 20) String phone, @NotNull UUID challengeId,
		@NotBlank @Pattern(regexp = "[0-9]{6}") String otp, @NotBlank @Size(max = 200) String password,
		@NotBlank @Size(min = 2, max = 30) String displayName, @Nullable @Size(max = 20) String termsVersion,
		@Nullable @Size(max = 20) String privacyVersion, @Nullable Boolean pushMarketing) {

	public RegisterCommand toCommand(String clientIp) {
		return new RegisterCommand(phone, challengeId, otp, password, displayName,
				new PolicyAcceptance(termsVersion, privacyVersion, Boolean.TRUE.equals(pushMarketing)), clientIp);
	}

}
