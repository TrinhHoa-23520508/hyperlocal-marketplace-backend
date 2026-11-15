package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyResetCodeRequest(@Schema(description = "From POST /auth/password/forgot") @NotNull UUID challengeId,
		@Schema(example = "0912345678") @NotBlank @Size(max = 20) String phone,
		@Schema(example = "123456") @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {

}
