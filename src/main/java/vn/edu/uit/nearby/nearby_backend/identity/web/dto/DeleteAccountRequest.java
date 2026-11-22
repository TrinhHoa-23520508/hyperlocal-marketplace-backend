package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
		@Schema(description = "Current password", example = "Secret123") @NotBlank @Size(max = 200) String password) {

}
