package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
		@Schema(example = "an.nguyen@example.com") @NotBlank @Email @Size(max = 254) String email) {

}
