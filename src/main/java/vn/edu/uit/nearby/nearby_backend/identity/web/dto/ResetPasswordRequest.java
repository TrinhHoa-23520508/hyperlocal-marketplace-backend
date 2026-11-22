package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@Schema(description = "From verify-otp or the email link") @NotBlank @Size(max = 100) String resetToken,
		@Schema(example = "NewSecret456") @NotBlank @Size(max = 200) String newPassword) {

}
