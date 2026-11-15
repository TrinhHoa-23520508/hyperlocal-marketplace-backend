package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyResetCodeRequest(@NotNull UUID challengeId, @NotBlank @Size(max = 20) String phone,
		@NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {

}
