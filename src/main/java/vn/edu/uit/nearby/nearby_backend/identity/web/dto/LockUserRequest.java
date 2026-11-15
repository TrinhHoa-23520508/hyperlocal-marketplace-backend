package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LockUserRequest(@Schema(description = "Stored in the audit log and shown to the user",
		example = "Repeated scam reports") @NotBlank @Size(max = 200) String reason) {

}
